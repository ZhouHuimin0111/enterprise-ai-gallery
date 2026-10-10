package io.github.zhouhuimin.gallery.auth;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.UserMapper;
import io.github.zhouhuimin.gallery.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

@Service @RequiredArgsConstructor
public class AuthService {
    private final UserMapper users; private final StringRedisTemplate redis; private final Jsons json;
    private final BCryptPasswordEncoder bcrypt=new BCryptPasswordEncoder(); private final SecureRandom random=new SecureRandom();
    private static final DefaultRedisScript<String> REFRESH=new DefaultRedisScript<>("local v=redis.call('GET',KEYS[1]); if v then redis.call('EXPIRE',KEYS[1],1800); end; return v;",String.class);
    private static final DefaultRedisScript<Long> RATE=new DefaultRedisScript<>("local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60); end; return n;",Long.class);
    public String randomToken() { byte[] bytes=new byte[32];random.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String key(String token) { return "session:"+DigestUtil.sha256Hex(token); }
    public String sessionHash(String token) { return DigestUtil.sha256Hex(token); }
    public void rateLimit(String category,String identity,int max) {
        Long n=redis.execute(RATE,List.of("rate:"+category+":"+DigestUtil.sha256Hex(identity)));
        BusinessException.require(n!=null && n<=max,ErrorCode.RATE_LIMITED);
    }
    public User register(String username,String password,String name) {
        BusinessException.require(!username.equalsIgnoreCase("admin")&&!username.equalsIgnoreCase("system"),ErrorCode.BAD_REQUEST);
        rateLimit("register",username.toLowerCase(Locale.ROOT),5);
        var user=new User();user.setUsername(username);user.setDisplayName(name.trim());user.setPasswordHash(bcrypt.encode(password));user.setRole("USER");users.insert(user);return user;
    }
    public Map<String,Object> login(String username,String password) {
        rateLimit("login",username.toLowerCase(Locale.ROOT),10);
        var user=users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,username));
        if(user==null || !Boolean.TRUE.equals(user.getEnabled()) || !user.getPasswordHash().startsWith("$2") || !bcrypt.matches(password,user.getPasswordHash())) throw new BusinessException(ErrorCode.UNAUTHORIZED,"用户名或密码错误");
        String token=randomToken();redis.opsForValue().set(key(token),user.getId()+":"+user.getAuthVersion(),Duration.ofMinutes(30));
        return Map.of("token",token,"user",actor(user));
    }
    public Actor authenticate(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) return null;
        return authenticateHash(sessionHash(token));
    }
    public Actor authenticateHash(String hash) {
        if(hash==null || !hash.matches("[a-f0-9]{64}")) return null;
        String value=redis.execute(REFRESH,List.of("session:"+hash));if(value==null) return null;
        String[] parts=value.split(":");var user=users.selectById(Long.parseLong(parts[0]));int version=parts.length>1?Integer.parseInt(parts[1]):0;
        return user==null || !Boolean.TRUE.equals(user.getEnabled()) || user.getAuthVersion()!=version?null:actor(user);
    }
    public void changePassword(Actor actor,String oldPassword,String nextPassword) {
        rateLimit("password",Long.toString(actor.id()),5);var user=users.selectById(actor.id());
        BusinessException.require(bcrypt.matches(oldPassword,user.getPasswordHash()),ErrorCode.UNAUTHORIZED);
        int changed=users.update(null,Wrappers.<User>lambdaUpdate().eq(User::getId,actor.id()).eq(User::getAuthVersion,user.getAuthVersion())
            .set(User::getPasswordHash,bcrypt.encode(nextPassword)).setSql("auth_version=auth_version+1"));
        BusinessException.require(changed==1,ErrorCode.CONFLICT);
    }
    public void logout(String token) { if(token!=null) redis.delete(key(token)); }
    public static Actor actor(User user) { return new Actor(user.getId(),user.getUsername(),user.getDisplayName(),user.getRole()); }
    public User bootstrap(String password) {
        BusinessException.require(password.length()>=12 && password.length()<=64 && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=72,ErrorCode.BAD_REQUEST);
        var existing=users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,"admin")); if(existing!=null) return existing;
        var user=new User();user.setUsername("admin");user.setDisplayName("图库管理员");user.setPasswordHash(bcrypt.encode(password));user.setRole("ADMIN");users.insert(user);return user;
    }
}
