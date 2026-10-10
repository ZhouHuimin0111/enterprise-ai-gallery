package io.github.zhouhuimin.gallery;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.ai.*;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import io.github.zhouhuimin.gallery.service.*;
import io.github.zhouhuimin.gallery.storage.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class CoreWorkflowIT {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4");
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource static void config(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("spring.data.redis.host",REDIS::getHost);r.add("spring.data.redis.port",()->REDIS.getMappedPort(6379));
        r.add("app.storage.local-path",()-> {try{return Files.createTempDirectory("gallery-it-").toString();}catch(Exception e){throw new IllegalStateException(e);}});
        r.add("app.bootstrap-admin-password",()->"TestAdmin2026!");r.add("app.demo-seed",()->false);r.add("app.ai.poll-ms",()->600000);
    }
    @Autowired AuthService auth;@Autowired SpaceService spaces;@Autowired AccessService access;@Autowired PictureService pictures;@Autowired AiJobService ai;
    @Autowired SpaceMapper spaceMapper;@Autowired PictureMapper pictureMapper;@Autowired AiJobMapper jobMapper;@Autowired UserMapper users;
    @Autowired org.springframework.data.redis.core.StringRedisTemplate redis;@Autowired Jsons json;@Autowired MockMvc mvc;
    @MockitoSpyBean DemoAiProvider demo;
    private static final AtomicInteger SEQUENCE=new AtomicInteger();
    Actor user() { var u=auth.register("testuser_"+SEQUENCE.incrementAndGet(),"TestUser2026!","测试用户");return AuthService.actor(u); }
    Actor admin() { return AuthService.actor(users.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername,"admin"))); }
    byte[] image() { return new ImageProcessor().encode(new BufferedImage(4,3,BufferedImage.TYPE_INT_RGB)).bytes(); }
    Picture upload(Actor a,long s) { return pictures.upload(a,s,image(),"测试素材","产品",List.of("设计","示例"),"UPLOAD"); }
    @Test void privatePicturesAreIsolatedEvenFromGlobalAdmin() {
        var a=user();var b=user();var s=spaces.create(a,"私有","PRIVATE");var p=upload(a,s.getId());
        assertThat(access.readPicture(a,p.getId()).getId()).isEqualTo(p.getId());
        assertThatThrownBy(()->access.readPicture(b,p.getId())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->access.readPicture(admin(),p.getId())).isInstanceOf(BusinessException.class);
    }
    @Test void onlyReviewedPublicPicturesAppearAndEditsInvalidateCatalog() {
        var a=user();var publicSpace=spaceMapper.selectOne(Wrappers.<Space>lambdaQuery().eq(Space::getType,"PUBLIC"));var p=upload(a,publicSpace.getId());
        var search=new PictureService.Search(publicSpace.getId(),null,null,null,null,null,"newest",1,50,false);
        assertThat(json.write(pictures.search(null,search))).doesNotContain("\"id\":"+p.getId()+",");
        assertThatThrownBy(()->access.readPicture(null,p.getId())).isInstanceOf(BusinessException.class);
        var reviewed=pictures.review(admin(),p.getId(),0,"APPROVED","通过");
        assertThat(json.write(pictures.search(null,search))).contains("测试素材");
        pictures.update(a,p.getId(),new PictureService.Metadata("修改后的图片","产品",List.of("更新"),reviewed.getVersion()));
        assertThat(json.write(pictures.search(null,search))).doesNotContain("修改后的图片");
        assertThat(pictureMapper.selectById(p.getId()).getReviewStatus()).isEqualTo("PENDING");
    }
    @Test void teamViewerCannotUploadAndRevocationBlocksRead() {
        var owner=user();var viewer=user();var s=spaces.create(owner,"团队","TEAM");var invite=spaces.invite(owner,s.getId(),"VIEWER");spaces.accept(viewer,(String)invite.get("token"));var p=upload(owner,s.getId());
        assertThat(access.readPicture(viewer,p.getId()).getId()).isEqualTo(p.getId());
        assertThatThrownBy(()->upload(viewer,s.getId())).isInstanceOf(BusinessException.class);
        spaces.member(owner,s.getId(),viewer.id(),"",true);assertThatThrownBy(()->access.readPicture(viewer,p.getId())).isInstanceOf(BusinessException.class);
    }
    @Test void teamAdministratorCannotPromoteOrRemoveOwner() {
        var owner=user();var member=user();var s=spaces.create(owner,"团队","TEAM");spaces.accept(member,(String)spaces.invite(owner,s.getId(),"ADMIN").get("token"));
        assertThatThrownBy(()->spaces.member(member,s.getId(),owner.id(),"VIEWER",false)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->spaces.invite(member,s.getId(),"ADMIN")).isInstanceOf(BusinessException.class);
    }
    @Test void invitationIsConsumedExactlyOnceUnderRace() throws Exception {
        var owner=user();var a=user();var b=user();var s=spaces.create(owner,"邀请","TEAM");String token=(String)spaces.invite(owner,s.getId(),"EDITOR").get("token");
        try(var pool=Executors.newFixedThreadPool(2)) {
            var futures=pool.invokeAll(List.of(()->{try{spaces.accept(a,token);return 1;}catch(BusinessException e){return 0;}},()->{try{spaces.accept(b,token);return 1;}catch(BusinessException e){return 0;}}));
            assertThat((Integer)futures.get(0).get()+(Integer)futures.get(1).get()).isEqualTo(1);
        }
    }
    @Test void atomicQuotaPreventsConcurrentUploadsFromExceedingLimit() throws Exception {
        var a=user();var s=spaces.create(a,"容量测试","PRIVATE");var limited=new Space();limited.setId(s.getId());limited.setMaxBytes((long)image().length*2);limited.setMaxCount(2);spaceMapper.updateById(limited);
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> requests=new ArrayList<>();for(int i=0;i<20;i++) requests.add(()->{try{upload(a,s.getId());return 1;}catch(BusinessException e){assertThat(e.code).isEqualTo(ErrorCode.QUOTA_EXCEEDED);return 0;}});
            int accepted=0;for(var f:pool.invokeAll(requests)) accepted+=f.get();assertThat(accepted).isEqualTo(2);
        }
        var actual=spaceMapper.selectById(s.getId());assertThat(actual.getUsedCount()).isEqualTo(2);assertThat(actual.getUsedBytes()).isEqualTo((long)image().length*2);
    }
    @Test void staleVersionLosesWithoutOverwritingConcurrentEdit() throws Exception {
        var owner=user();var s=spaces.create(owner,"版本","TEAM");var p=upload(owner,s.getId());
        try(var pool=Executors.newFixedThreadPool(8)) {
            List<Callable<Integer>> requests=new ArrayList<>();for(int i=0;i<16;i++) {int n=i;requests.add(()->{try{pictures.update(owner,p.getId(),new PictureService.Metadata("版本 "+n,"测试",List.of("新标签"),0));return 1;}catch(BusinessException e){assertThat(e.code).isEqualTo(ErrorCode.CONFLICT);return 0;}});}
            int accepted=0;for(var f:pool.invokeAll(requests)) accepted+=f.get();assertThat(accepted).isEqualTo(1);
        }
        assertThat(pictureMapper.selectById(p.getId()).getVersion()).isEqualTo(1);assertThat(pictureMapper.selectById(p.getId()).getTags()).containsExactly("新标签");
    }
    @Test void batchPermissionFailureLeavesAllRowsAndQuotaUntouched() {
        var owner=user();var other=user();var s=spaces.create(owner,"批量","PRIVATE");var foreign=spaces.create(other,"他人","PRIVATE");var p=upload(owner,s.getId());var hidden=upload(other,foreign.getId());
        assertThatThrownBy(()->pictures.batch(owner,List.of(p.getId(),hidden.getId()),Map.of(p.getId(),0,hidden.getId(),0),true,"",List.of())).isInstanceOf(BusinessException.class);
        assertThat(pictureMapper.selectById(p.getId())).isNotNull();assertThat(spaceMapper.selectById(s.getId()).getUsedCount()).isEqualTo(1);
    }
    @Test void jsonTagSearchIsParameterizedAndPaginationBounded() {
        var owner=user();var s=spaces.create(owner,"检索","PRIVATE");upload(owner,s.getId());
        var result=pictures.search(owner,new PictureService.Search(s.getId(),null,null,"设计",null,null,"newest",1,24,false));assertThat(json.write(result)).contains("测试素材");
        assertThat(json.write(pictures.search(owner,new PictureService.Search(s.getId(),null,null,"\" OR 1=1 --",null,null,"newest",1,24,false)))).contains("\"total\":0");
        assertThatThrownBy(()->pictures.search(owner,new PictureService.Search(s.getId(),null,null,null,null,null,"newest",1,500,false))).isInstanceOf(BusinessException.class);
    }
    @Test void cropAndDeleteMaintainExactQuota() {
        var owner=user();var s=spaces.create(owner,"裁剪","PRIVATE");var p=upload(owner,s.getId());
        var edited=pictures.transform(owner,p.getId(),0,0,0,2,3,90);assertThat(edited.getWidth()).isEqualTo(3);assertThat(edited.getHeight()).isEqualTo(2);
        assertThat(spaceMapper.selectById(s.getId()).getUsedBytes()).isEqualTo(edited.getBytes());pictures.delete(owner,p.getId(),edited.getVersion());
        assertThat(spaceMapper.selectById(s.getId()).getUsedBytes()).isZero();assertThat(spaceMapper.selectById(s.getId()).getUsedCount()).isZero();
    }
    @Test void aiIdempotencyAndReplayCreateOnePictureAtomically() {
        var owner=user();var s=spaces.create(owner,"AI","PRIVATE");var job=ai.create(owner,s.getId(),"a garden","AI测试","request-123");
        assertThat(ai.create(owner,s.getId(),"a garden","AI测试","request-123").getId()).isEqualTo(job.getId());
        assertThatThrownBy(()->ai.create(owner,s.getId(),"different","AI测试","request-123")).isInstanceOf(BusinessException.class);
        ai.process(job.getId());ai.process(job.getId());ai.process(job.getId());assertThat(jobMapper.selectById(job.getId()).getStatus()).isEqualTo("SUCCEEDED");
        assertThat(spaceMapper.selectById(s.getId()).getUsedCount()).isEqualTo(1);
        var picture=pictureMapper.selectById(jobMapper.selectById(job.getId()).getPictureId());assertThat(picture.getSource()).isEqualTo("DEMO");
        pictures.delete(owner,picture.getId(),picture.getVersion());assertThat(jobMapper.selectById(job.getId()).getPictureId()).isNull();
    }
    @Test void uncertainAiSubmissionNeverAutomaticallyResubmits() {
        var owner=user();var s=spaces.create(owner,"AI故障","PRIVATE");doThrow(new BusinessException(ErrorCode.AI_UNAVAILABLE,"AI_TRANSPORT_UNCERTAIN")).when(demo).submit(eq("uncertain"),anyString());
        var job=ai.create(owner,s.getId(),"uncertain","故障任务","uncertain-key");ai.process(job.getId());ai.process(job.getId());
        assertThat(jobMapper.selectById(job.getId()).getStatus()).isEqualTo("UNKNOWN");verify(demo,times(1)).submit("uncertain",job.getId());assertThat(spaceMapper.selectById(s.getId()).getUsedCount()).isZero();
    }
    @Test void sharedSessionRefreshAndLogoutAreAtomic() {
        var owner=user();String token=(String)auth.login(owner.username(),"TestUser2026!").get("token");String key="session:"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(token);redis.expire(key,Duration.ofSeconds(2));
        assertThat(auth.authenticate(token).id()).isEqualTo(owner.id());assertThat(redis.getExpire(key)).isGreaterThan(1700);auth.logout(token);assertThat(auth.authenticate(token)).isNull();
    }
    @Test void httpErrorsAreUniformAndDocsUseOpenApi3() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"x\",\"password\":\"1\",\"displayName\":\"x\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        var result=mvc.perform(get("/v3/api-docs")).andReturn();assertThat(result.getResolvedException()).isNull();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);assertThat(json.read(result.getResponse().getContentAsString()).path("openapi").asText()).startsWith("3.");
    }
    @Test void passwordChangeInvalidatesAllOldSessions() {
        var owner=user();String first=(String)auth.login(owner.username(),"TestUser2026!").get("token"),second=(String)auth.login(owner.username(),"TestUser2026!").get("token");
        auth.changePassword(owner,"TestUser2026!","NextPassword2026!");assertThat(auth.authenticate(first)).isNull();assertThat(auth.authenticate(second)).isNull();
        assertThatThrownBy(()->auth.login(owner.username(),"TestUser2026!")).isInstanceOf(BusinessException.class);assertThat(auth.login(owner.username(),"NextPassword2026!")).containsKey("token");
    }
    @Test void emptySpaceCanBeDeletedButNonEmptySpaceCannot() {
        var owner=user();var empty=spaces.create(owner,"空空间","TEAM");spaces.delete(owner,empty.getId());assertThat(spaceMapper.selectById(empty.getId())).isNull();
        var full=spaces.create(owner,"非空空间","PRIVATE");upload(owner,full.getId());assertThatThrownBy(()->spaces.delete(owner,full.getId())).isInstanceOf(BusinessException.class);assertThat(spaceMapper.selectById(full.getId())).isNotNull();
    }
    @Test void suspendedUserLosesSessionAndCannotLogin() throws Exception {
        var owner=user();String token=(String)auth.login(owner.username(),"TestUser2026!").get("token"),adminToken=(String)auth.login("admin","TestAdmin2026!").get("token");
        mvc.perform(delete("/api/admin/users/"+owner.id()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        assertThat(auth.authenticate(token)).isNull();assertThatThrownBy(()->auth.login(owner.username(),"TestUser2026!")).isInstanceOf(BusinessException.class);
    }
}
