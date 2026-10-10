package io.github.zhouhuimin.gallery.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.zhouhuimin.gallery.auth.Actor;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import io.github.zhouhuimin.gallery.storage.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PictureService {
    private final PictureMapper pictures;private final SpaceMapper spaces;private final MaintenanceMapper maintenance;
    private final AccessService access;private final ObjectStore store;private final ImageProcessor processor;
    private final TransactionTemplate tx;private final ChangeEvents events;private final StringRedisTemplate redis;private final Jsons json;
    public record Metadata(String title,String category,List<String> tags,int version) {}
    public record Search(long spaceId,String q,String category,String tag,Integer minWidth,Integer minHeight,String sort,int page,int size,boolean mine) {}
    public Map<String,Object> view(Picture p) {
        var out=new LinkedHashMap<String,Object>();out.put("id",p.getId());out.put("spaceId",p.getSpaceId());out.put("uploaderId",p.getUploaderId());out.put("title",p.getTitle());out.put("category",p.getCategory());out.put("tags",p.getTags());
        out.put("width",p.getWidth());out.put("height",p.getHeight());out.put("bytes",p.getBytes());out.put("version",p.getVersion());out.put("reviewStatus",p.getReviewStatus());out.put("reviewReason",p.getReviewReason());
        out.put("source",p.getSource());out.put("createdAt",p.getCreatedAt());out.put("updatedAt",p.getUpdatedAt());out.put("contentUrl","/api/pictures/"+p.getId()+"/content");return out;
    }
    public static void validateMetadata(String title,String category,List<String> tags) {
        BusinessException.require(title!=null && !title.isBlank() && title.length()<=100 && category!=null && category.length()<=40 && tags!=null && tags.size()<=10,ErrorCode.BAD_REQUEST);
        BusinessException.require(tags.stream().allMatch(t->t!=null && !t.isBlank() && t.length()<=30),ErrorCode.BAD_REQUEST);
    }
    public String stage(ImageProcessor.Image image) {
        String key="objects/"+UUID.randomUUID()+".png";
        // Persist cleanup intent before touching the external object store; clear it in the DB commit that links the object.
        maintenance.enqueueGc(key);store.put(key,image.bytes(),"image/png");return key;
    }
    public Picture upload(Actor actor,long spaceId,byte[] bytes,String title,String category,List<String> tags,String source) {
        var space=access.writeSpace(actor,spaceId);validateMetadata(title,category,tags);
        var image=processor.normalize(bytes);String key=stage(image);
        return tx.execute(status->insert(actor,space,image,key,title,category,tags,source));
    }
    public Picture insert(Actor actor,Space space,ImageProcessor.Image image,String key,String title,String category,List<String> tags,String source) {
        access.writeSpace(actor,space.getId());
        BusinessException.require(spaces.adjustQuota(space.getId(),image.bytes().length,1)==1,ErrorCode.QUOTA_EXCEEDED);
        var p=new Picture();p.setSpaceId(space.getId());p.setUploaderId(actor.id());p.setTitle(title.trim());p.setCategory(category.trim());p.setTags(tags.stream().map(String::trim).distinct().toList());
        p.setObjectKey(key);p.setSha256(image.sha256());p.setMediaType("image/png");p.setWidth(image.width());p.setHeight(image.height());p.setBytes((long)image.bytes().length);
        p.setReviewStatus("PUBLIC".equals(space.getType())?"PENDING":"APPROVED");p.setReviewReason("");p.setSource(source);p.setVersion(0);
        var now=LocalDateTime.now(ZoneOffset.UTC);p.setCreatedAt(now);p.setUpdatedAt(now);pictures.insert(p);maintenance.gcDone(key);
        maintenance.audit(actor.id(),space.getId(),"PICTURE_UPLOAD",p.getId().toString());events.picture(p);return p;
    }
    public Object search(Actor actor,Search s) {
        BusinessException.require(s.page>=1 && s.page<=10000 && s.size>=1 && s.size<=50 && (s.q==null||s.q.length()<=100) && (s.category==null||s.category.length()<=40) && (s.tag==null||s.tag.length()<=30),ErrorCode.BAD_REQUEST);
        BusinessException.require((s.minWidth==null||s.minWidth>=0&&s.minWidth<=100000)&&(s.minHeight==null||s.minHeight>=0&&s.minHeight<=100000),ErrorCode.BAD_REQUEST);
        var space=access.readSpace(actor,s.spaceId);
        if(s.mine && actor==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        boolean cache="PUBLIC".equals(space.getType()) && !s.mine;
        String key=null;
        if(cache) {
            String revision=redis.opsForValue().get("catalog:revision");key="catalog:"+Objects.toString(revision,"0")+":"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(json.write(s));
            String value=redis.opsForValue().get(key);if(value!=null) return json.read(value);
        }
        var query=Wrappers.<Picture>lambdaQuery().eq(Picture::getSpaceId,s.spaceId);
        if(cache) query.eq(Picture::getReviewStatus,"APPROVED");
        if(s.mine) query.eq(Picture::getUploaderId,actor.id());
        if(s.q!=null && !s.q.isBlank()) query.like(Picture::getTitle,s.q.trim());
        if(s.category!=null && !s.category.isBlank()) query.eq(Picture::getCategory,s.category);
        if(s.tag!=null && !s.tag.isBlank()) query.apply("JSON_CONTAINS(tags,{0})",json.write(s.tag));
        if(s.minWidth!=null) query.ge(Picture::getWidth,s.minWidth);if(s.minHeight!=null) query.ge(Picture::getHeight,s.minHeight);
        String sort=Objects.toString(s.sort,"newest");
        switch(sort) { case "newest" -> query.orderByDesc(Picture::getCreatedAt,Picture::getId);case "oldest" -> query.orderByAsc(Picture::getCreatedAt,Picture::getId);case "largest" -> query.orderByDesc(Picture::getBytes,Picture::getId);default -> throw new BusinessException(ErrorCode.BAD_REQUEST); }
        var page=pictures.selectPage(new Page<>(s.page,s.size),query);
        var result=Map.of("records",page.getRecords().stream().map(this::view).toList(),"total",page.getTotal(),"page",s.page,"size",s.size);
        if(key!=null) redis.opsForValue().set(key,json.write(result),Duration.ofSeconds(30));return result;
    }
    @Transactional public Picture update(Actor actor,long id,Metadata metadata) {
        var p=access.writePicture(actor,id);validateMetadata(metadata.title,metadata.category,metadata.tags);
        BusinessException.require(p.getVersion()==metadata.version,ErrorCode.CONFLICT);
        boolean publicSpace="PUBLIC".equals(access.space(p.getSpaceId()).getType());
        String review=publicSpace?"PENDING":p.getReviewStatus();
        int changed=pictures.update(null,Wrappers.<Picture>lambdaUpdate().eq(Picture::getId,id).eq(Picture::getVersion,metadata.version)
            .set(Picture::getTitle,metadata.title.trim()).set(Picture::getCategory,metadata.category.trim())
            .setSql("tags={0}",json.write(metadata.tags.stream().map(String::trim).distinct().toList()))
            .set(Picture::getReviewStatus,review).set(Picture::getReviewReason,"").setSql("version=version+1").set(Picture::getUpdatedAt,LocalDateTime.now(ZoneOffset.UTC)));
        BusinessException.require(changed==1,ErrorCode.CONFLICT);var fresh=pictures.selectById(id);
        maintenance.audit(actor.id(),p.getSpaceId(),"PICTURE_EDIT",Long.toString(id));events.picture(fresh);return fresh;
    }
    public Picture transform(Actor actor,long id,int version,int x,int y,int width,int height,int rotation) {
        var original=access.writePicture(actor,id);BusinessException.require(original.getVersion()==version,ErrorCode.CONFLICT);
        var image=processor.transform(store.read(original.getObjectKey()),x,y,width,height,rotation);String key=stage(image);
        return tx.execute(status-> {
            var p=access.writePicture(actor,id);BusinessException.require(p.getVersion()==version,ErrorCode.CONFLICT);
            BusinessException.require(spaces.adjustQuota(p.getSpaceId(),image.bytes().length-p.getBytes(),0)==1,ErrorCode.QUOTA_EXCEEDED);
            String review="PUBLIC".equals(access.space(p.getSpaceId()).getType())?"PENDING":p.getReviewStatus();
            int changed=pictures.update(null,Wrappers.<Picture>lambdaUpdate().eq(Picture::getId,id).eq(Picture::getVersion,version)
                .set(Picture::getObjectKey,key).set(Picture::getSha256,image.sha256()).set(Picture::getWidth,image.width()).set(Picture::getHeight,image.height())
                .set(Picture::getBytes,image.bytes().length).set(Picture::getMediaType,"image/png").set(Picture::getReviewStatus,review).set(Picture::getReviewReason,"")
                .setSql("version=version+1").set(Picture::getUpdatedAt,LocalDateTime.now(ZoneOffset.UTC)));
            BusinessException.require(changed==1,ErrorCode.CONFLICT);maintenance.enqueueGc(p.getObjectKey());maintenance.gcDone(key);
            var fresh=pictures.selectById(id);maintenance.audit(actor.id(),p.getSpaceId(),"PICTURE_TRANSFORM",Long.toString(id));events.picture(fresh);return fresh;
        });
    }
    @Transactional public void delete(Actor actor,long id,int version) {
        var p=access.writePicture(actor,id);BusinessException.require(p.getVersion()==version,ErrorCode.CONFLICT);
        // Lock space quota before the row, matching the upload/transform lock order.
        BusinessException.require(spaces.adjustQuota(p.getSpaceId(),-p.getBytes(),-1)==1,ErrorCode.CONFLICT);
        int deleted=pictures.delete(Wrappers.<Picture>lambdaQuery().eq(Picture::getId,id).eq(Picture::getVersion,version));
        BusinessException.require(deleted==1,ErrorCode.CONFLICT);maintenance.enqueueGc(p.getObjectKey());
        maintenance.audit(actor.id(),p.getSpaceId(),"PICTURE_DELETE",Long.toString(id));p.setVersion(version+1);events.picture(p);
    }
    @Transactional public List<Map<String,Object>> batch(Actor actor,List<Long> ids,Map<Long,Integer> versions,boolean remove,String category,List<String> tags) {
        BusinessException.require(!ids.isEmpty() && ids.size()<=50 && new HashSet<>(ids).size()==ids.size(),ErrorCode.BAD_REQUEST);
        var sorted=ids.stream().sorted().toList();Long scope=null;
        for(long id:sorted) { var p=access.writePicture(actor,id);BusinessException.require(Objects.equals(versions.get(id),p.getVersion()),ErrorCode.CONFLICT);if(scope==null) scope=p.getSpaceId();BusinessException.require(scope.equals(p.getSpaceId()),ErrorCode.BAD_REQUEST); }
        var output=new ArrayList<Map<String,Object>>();
        for(long id:sorted) { var p=access.writePicture(actor,id);if(remove) delete(actor,id,versions.get(id));else output.add(view(update(actor,id,new Metadata(p.getTitle(),category,tags,versions.get(id))))); }
        return output;
    }
    @Transactional public Picture review(Actor actor,long id,int version,String status,String reason) {
        BusinessException.require(actor.admin(),ErrorCode.FORBIDDEN);BusinessException.require(Set.of("APPROVED","REJECTED").contains(status),ErrorCode.BAD_REQUEST);
        var p=access.readPicture(actor,id);BusinessException.require("PUBLIC".equals(access.space(p.getSpaceId()).getType()),ErrorCode.BAD_REQUEST);
        int changed=pictures.update(null,Wrappers.<Picture>lambdaUpdate().eq(Picture::getId,id).eq(Picture::getVersion,version)
            .set(Picture::getReviewStatus,status).set(Picture::getReviewReason,reason).set(Picture::getReviewerId,actor.id())
            .setSql("version=version+1").set(Picture::getUpdatedAt,LocalDateTime.now(ZoneOffset.UTC)));
        BusinessException.require(changed==1,ErrorCode.CONFLICT);var fresh=pictures.selectById(id);maintenance.audit(actor.id(),p.getSpaceId(),"PICTURE_REVIEW",Long.toString(id));events.picture(fresh);return fresh;
    }
}
