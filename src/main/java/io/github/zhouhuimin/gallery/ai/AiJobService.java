package io.github.zhouhuimin.gallery.ai;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import io.github.zhouhuimin.gallery.service.*;
import io.github.zhouhuimin.gallery.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class AiJobService {
    private final AiJobMapper jobs;private final UserMapper users;private final AccessService access;private final AuthService auth;
    private final AiProvider provider;private final PictureService pictures;private final ImageProcessor images;private final TransactionTemplate tx;
    private final StringRedisTemplate redis;private final MaintenanceMapper maintenance;
    private static final DefaultRedisScript<Long> UNLOCK=new DefaultRedisScript<>("if redis.call('GET',KEYS[1])==ARGV[1] then return redis.call('DEL',KEYS[1]) end;return 0;",Long.class);
    @org.springframework.transaction.annotation.Transactional
    public AiJob create(Actor actor,long spaceId,String prompt,String title,String idempotencyKey) {
        // Serializing creation per user makes the active-job limit independent of concurrent HTTP requests.
        var lockedUser=users.lock(actor.id());BusinessException.require(lockedUser!=null && Boolean.TRUE.equals(lockedUser.getEnabled()),ErrorCode.FORBIDDEN);
        access.writeSpace(actor,spaceId);
        var existing=jobs.selectOne(Wrappers.<AiJob>lambdaQuery().eq(AiJob::getUserId,actor.id()).eq(AiJob::getIdempotencyKey,idempotencyKey));
        if(existing!=null) { sameRequest(existing,spaceId,prompt,title);return existing; }
        auth.rateLimit("ai",Long.toString(actor.id()),5);
        BusinessException.require(jobs.selectCount(Wrappers.<AiJob>lambdaQuery().eq(AiJob::getUserId,actor.id()).in(AiJob::getStatus,List.of("QUEUED","SUBMITTING","SUBMITTED")))<3,ErrorCode.RATE_LIMITED);
        var job=new AiJob();job.setId(UUID.randomUUID().toString());job.setUserId(actor.id());job.setSpaceId(spaceId);job.setPrompt(prompt.trim());job.setTitle(title.trim());job.setIdempotencyKey(idempotencyKey);
        job.setProvider(provider.mode());job.setStatus("QUEUED");job.setAttempts(0);job.setCreatedAt(now());job.setUpdatedAt(now());
        try { jobs.insert(job); } catch(DuplicateKeyException e) { var found=jobs.selectOne(Wrappers.<AiJob>lambdaQuery().eq(AiJob::getUserId,actor.id()).eq(AiJob::getIdempotencyKey,idempotencyKey));sameRequest(found,spaceId,prompt,title);return found; }
        return job;
    }
    private void sameRequest(AiJob j,long space,String prompt,String title) { BusinessException.require(j!=null && j.getSpaceId()==space && j.getPrompt().equals(prompt.trim()) && j.getTitle().equals(title.trim()),ErrorCode.CONFLICT); }
    public AiJob get(Actor actor,String id) { var job=jobs.selectById(id);BusinessException.require(job!=null,ErrorCode.NOT_FOUND);BusinessException.require(job.getUserId()==actor.id(),ErrorCode.FORBIDDEN);return job; }
    public Map<String,Object> view(AiJob j) {
        var out=new LinkedHashMap<String,Object>();out.put("id",j.getId());out.put("spaceId",j.getSpaceId());out.put("title",j.getTitle());out.put("provider",j.getProvider());out.put("status",j.getStatus());out.put("pictureId",j.getPictureId());out.put("errorCode",j.getErrorCode());out.put("createdAt",j.getCreatedAt());return out;
    }
    private LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC); }
    @Scheduled(fixedDelayString="${app.ai.poll-ms}",initialDelay=3000)
    public void tick() {
        var candidates=jobs.selectList(Wrappers.<AiJob>lambdaQuery().in(AiJob::getStatus,List.of("QUEUED","SUBMITTING","SUBMITTED")).orderByAsc(AiJob::getUpdatedAt).last("LIMIT 10"));
        for(var job:candidates) process(job.getId());
    }
    public void process(String id) {
        String lock="ai:lock:"+id,token=UUID.randomUUID().toString();
        if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lock,token,Duration.ofSeconds(90)))) return;
        try {
            var job=jobs.selectById(id);if(job==null) return;
            if(!job.getProvider().equals(provider.mode())) { fail(id,job.getStatus(),"FAILED","PROVIDER_CONFIGURATION_CHANGED");return; }
            var user=users.selectById(job.getUserId());BusinessException.require(user!=null && Boolean.TRUE.equals(user.getEnabled()),ErrorCode.FORBIDDEN);var actor=AuthService.actor(user);
            if("SUBMITTING".equals(job.getStatus())) {
                if(job.getUpdatedAt().isBefore(now().minusMinutes(2))) fail(id,"SUBMITTING","UNKNOWN","SUBMISSION_RESULT_UNCERTAIN");return;
            }
            if("QUEUED".equals(job.getStatus())) {
                access.writeSpace(actor,job.getSpaceId());
                if(jobs.transition(id,"QUEUED","SUBMITTING")!=1) return;
                try {
                    String providerId=provider.submit(job.getPrompt(),id);
                    jobs.update(null,Wrappers.<AiJob>lambdaUpdate().eq(AiJob::getId,id).eq(AiJob::getStatus,"SUBMITTING")
                        .set(AiJob::getProviderTaskId,providerId).set(AiJob::getStatus,"SUBMITTED").set(AiJob::getUpdatedAt,now()));
                } catch(Exception e) {
                    // Submission transport timeouts or persistence errors are uncertain. Never automatically submit twice.
                    boolean rejected=e instanceof BusinessException b && (b.getMessage().startsWith("AI_HTTP_4") && !b.getMessage().equals("AI_HTTP_429") || b.getMessage().equals("AI_PROVIDER_REJECTED"));
                    fail(id,"SUBMITTING",rejected?"FAILED":"UNKNOWN",rejected?"AI_REQUEST_REJECTED":"SUBMISSION_RESULT_UNCERTAIN");
                }return;
            }
            if(!"SUBMITTED".equals(job.getStatus())) return;
            if(job.getCreatedAt().isBefore(now().minusHours(23))) { fail(id,"SUBMITTED","FAILED","AI_TASK_EXPIRED");return; }
            var result=provider.poll(job.getProviderTaskId());
            if("FAILED".equals(result.status())) { fail(id,"SUBMITTED","FAILED",result.errorCode());return; }
            if(!"SUCCEEDED".equals(result.status())) { jobs.update(null,Wrappers.<AiJob>lambdaUpdate().eq(AiJob::getId,id).eq(AiJob::getStatus,"SUBMITTED").set(AiJob::getUpdatedAt,now()));return; }
            var image=images.normalize(result.image());var space=access.writeSpace(actor,job.getSpaceId());String objectKey=pictures.stage(image);
            tx.executeWithoutResult(status-> {
                var locked=jobs.lock(id);if(locked==null || !"SUBMITTED".equals(locked.getStatus())) return;
                var p=pictures.insert(actor,space,image,objectKey,job.getTitle(),"AI",List.of("AI"),"demo".equals(provider.mode())?"DEMO":"AI");
                jobs.update(null,Wrappers.<AiJob>lambdaUpdate().eq(AiJob::getId,id).eq(AiJob::getStatus,"SUBMITTED").set(AiJob::getStatus,"SUCCEEDED").set(AiJob::getPictureId,p.getId()).set(AiJob::getUpdatedAt,now()));
                maintenance.audit(actor.id(),space.getId(),"AI_COMPLETE",id);
            });
        } catch(BusinessException e) {
            var job=jobs.selectById(id);
            if(job!=null && Set.of(ErrorCode.FORBIDDEN,ErrorCode.QUOTA_EXCEEDED,ErrorCode.BAD_REQUEST).contains(e.code)) fail(id,job.getStatus(),"FAILED",e.code.name());
            else retryFailure(id);
        } catch(Exception e) { retryFailure(id); }
        finally { redis.execute(UNLOCK,List.of(lock),token); }
    }
    private void retryFailure(String id) {
        jobs.update(null,Wrappers.<AiJob>lambdaUpdate().eq(AiJob::getId,id).eq(AiJob::getStatus,"SUBMITTED").setSql("attempts=attempts+1").set(AiJob::getUpdatedAt,now()));
        var j=jobs.selectById(id);if(j!=null && j.getAttempts()>=20) fail(id,"SUBMITTED","FAILED","AI_POLL_RETRY_EXHAUSTED");
        log.warn("AI worker transient failure job={}",id);
    }
    private void fail(String id,String previous,String next,String code) {
        jobs.update(null,Wrappers.<AiJob>lambdaUpdate().eq(AiJob::getId,id).eq(AiJob::getStatus,previous).set(AiJob::getStatus,next).set(AiJob::getErrorCode,code).set(AiJob::getUpdatedAt,now()));
    }
}
