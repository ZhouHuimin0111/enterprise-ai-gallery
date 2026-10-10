package io.github.zhouhuimin.gallery.config;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.ai.DemoAiProvider;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import io.github.zhouhuimin.gallery.service.PictureService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Duration;
import java.util.*;

@Component @RequiredArgsConstructor
public class BootstrapData implements ApplicationRunner {
    private final AuthService auth;private final UserMapper users;private final SpaceMapper spaces;private final PictureService pictures;private final StringRedisTemplate redis;private final TransactionTemplate tx;
    @Value("${app.bootstrap-admin-password}") private String password;@Value("${app.demo-seed}") private boolean demo;
    @Override public void run(ApplicationArguments args) {
        if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("bootstrap:lease","initializing",Duration.ofMinutes(5)))) return;
        try {
            User admin;
            if(!password.isBlank()) admin=auth.bootstrap(password);
            else {
                admin=users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,"system"));
                if(admin==null) { admin=new User();admin.setUsername("system");admin.setDisplayName("FrameHub");admin.setRole("USER");admin.setPasswordHash("DISABLED");users.insert(admin); }
            }
            var publicSpace=spaces.selectOne(Wrappers.<Space>lambdaQuery().eq(Space::getType,"PUBLIC"));boolean first=publicSpace==null;
            if(first) { publicSpace=new Space();publicSpace.setName("公共灵感图库");publicSpace.setType("PUBLIC");publicSpace.setOwnerId(admin.getId());publicSpace.setMaxBytes(1073741824L);publicSpace.setMaxCount(10000);publicSpace.setUsedBytes(0L);publicSpace.setUsedCount(0);spaces.insert(publicSpace); }
            if(first && demo && "ADMIN".equals(admin.getRole())) {
                String[] titles={"晨光几何 · 品牌探索","城市轮廓 · 空间研究","纸上花园 · 色彩灵感","柔和秩序 · 产品构成","蓝色时刻 · 创意素材","构筑之间 · 视觉实验"};
                String[] categories={"品牌","空间","插画","产品","创意","空间"};
                for(int i=0;i<titles.length;i++) {
                    var p=pictures.upload(AuthService.actor(admin),publicSpace.getId(),DemoAiProvider.art(127+i*57,"FRAMEHUB / ORIGINAL DEMO ART"),titles[i],categories[i],List.of("示例","几何","设计"),"DEMO");
                    pictures.review(AuthService.actor(admin),p.getId(),0,"APPROVED","本地原创程序化演示素材");
                }
            }
        } finally { redis.delete("bootstrap:lease"); }
    }
}
