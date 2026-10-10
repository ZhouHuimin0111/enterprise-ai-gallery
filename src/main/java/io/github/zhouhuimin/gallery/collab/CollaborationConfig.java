package io.github.zhouhuimin.gallery.collab;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.UserMapper;
import io.github.zhouhuimin.gallery.service.AccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.*;
import org.springframework.http.*;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import java.util.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@Configuration @EnableWebSocket @RequiredArgsConstructor
public class CollaborationConfig implements WebSocketConfigurer {
    private final CollaborationHub hub;private final StringRedisTemplate redis;private final Jsons json;private final AccessService access;private final UserMapper users;
    @Value("${app.allowed-origins}") private String origins;
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(hub,"/ws/collaborate").setAllowedOrigins(origins.split(",")).addInterceptors(new HandshakeInterceptor() {
            @Override public boolean beforeHandshake(org.springframework.http.server.ServerHttpRequest request,org.springframework.http.server.ServerHttpResponse response,WebSocketHandler handler,Map<String,Object> attributes) {
                try {
                    String origin=request.getHeaders().getOrigin();if(origin==null||!Arrays.asList(origins.split(",")).contains(origin)) { response.setStatusCode(HttpStatus.FORBIDDEN);return false; }
                    String query=request.getURI().getRawQuery();if(query==null||!query.matches("ticket=[A-Za-z0-9_-]{43}")) { response.setStatusCode(HttpStatus.UNAUTHORIZED);return false; }
                    String ticket=URLDecoder.decode(query.substring(7),StandardCharsets.UTF_8);
                    String value=redis.opsForValue().getAndDelete("ws:ticket:"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(ticket));
                    if(value==null) { response.setStatusCode(HttpStatus.UNAUTHORIZED);return false; }
                    var data=json.read(value);long userId=data.path("userId").asLong(),pictureId=data.path("pictureId").asLong();
                    var user=users.selectById(userId);var p=access.readPicture(AuthService.actor(user),pictureId);
                    BusinessException.require("TEAM".equals(access.space(p.getSpaceId()).getType()),ErrorCode.FORBIDDEN);
                    attributes.put("userId",userId);attributes.put("pictureId",pictureId);return true;
                } catch(Exception e) { response.setStatusCode(HttpStatus.FORBIDDEN);return false; }
            }
            @Override public void afterHandshake(org.springframework.http.server.ServerHttpRequest request,org.springframework.http.server.ServerHttpResponse response,WebSocketHandler handler,Exception e) {}
        });
    }
    @Bean RedisMessageListenerContainer changes(RedisConnectionFactory factory) {
        var container=new RedisMessageListenerContainer();container.setConnectionFactory(factory);container.addMessageListener(hub,new ChannelTopic("gallery:changes"));return container;
    }
}
