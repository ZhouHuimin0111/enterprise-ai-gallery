package io.github.zhouhuimin.gallery.service;
import io.github.zhouhuimin.gallery.common.Jsons;
import io.github.zhouhuimin.gallery.model.Picture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import java.util.Map;

@Component @RequiredArgsConstructor @Slf4j
public class ChangeEvents {
    private final StringRedisTemplate redis;private final Jsons json;
    public void afterCommit(Runnable work) {
        var safe=new Runnable() { public void run() { try { work.run(); } catch(Exception e) { log.warn("Post-commit notification unavailable type={}",e.getClass().getSimpleName()); } } };
        if(TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { safe.run(); } });
        else safe.run();
    }
    public void picture(Picture p) {
        afterCommit(()-> {
            redis.opsForValue().increment("catalog:revision");
            redis.convertAndSend("gallery:changes",json.write(Map.of("type","PICTURE_CHANGED","pictureId",p.getId(),"spaceId",p.getSpaceId(),"version",p.getVersion())));
        });
    }
}
