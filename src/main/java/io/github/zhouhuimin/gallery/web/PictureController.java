package io.github.zhouhuimin.gallery.web;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.PictureMapper;
import io.github.zhouhuimin.gallery.model.Picture;
import io.github.zhouhuimin.gallery.service.*;
import io.github.zhouhuimin.gallery.storage.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController @RequestMapping("/api/pictures") @RequiredArgsConstructor
public class PictureController {
    private final PictureService service;private final AccessService access;private final ObjectStore storage;private final Jsons json;private final PictureMapper pictures;private final AuthService auth;
    public record Edit(@NotBlank @Size(max=100) String title,@NotNull @Size(max=40) String category,@NotNull @Size(max=10) List<@NotBlank @Size(max=30) String> tags,@Min(0) int version) {}
    public record Transform(@Min(0) int version,@Min(0) int x,@Min(0) int y,@Min(1) int width,@Min(1) int height,int rotation) {}
    public record Batch(@NotEmpty @Size(max=50) List<Long> ids,@NotNull Map<Long,Integer> versions,boolean remove,@NotNull @Size(max=40) String category,@NotNull @Size(max=10) List<@NotBlank @Size(max=30) String> tags) {}
    public record Review(@Min(0) int version,@NotBlank String status,@NotNull @Size(max=200) String reason) {}
    @Operation(summary="分页搜索；公共只返回通过审核的图片，私有/团队先检查成员权限") @GetMapping
    ApiResponse<Object> search(@RequestParam long spaceId,@RequestParam(required=false) String q,@RequestParam(required=false) String category,
        @RequestParam(required=false) String tag,@RequestParam(required=false) Integer minWidth,@RequestParam(required=false) Integer minHeight,
        @RequestParam(defaultValue="newest") String sort,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="24") int size,
        @RequestParam(defaultValue="false") boolean mine,HttpServletRequest req) {
        return ApiResponse.ok(service.search(Actor.optional(req),new PictureService.Search(spaceId,q,category,tag,minWidth,minHeight,sort,page,size,mine)));
    }
    @Operation(summary="上传 PNG/JPEG，检查内容/像素/容量；公开图片进入待审核") @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ApiResponse<Map<String,Object>> upload(@RequestParam MultipartFile file,@RequestParam long spaceId,@RequestParam String title,
        @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="[]") String tags,HttpServletRequest req) throws java.io.IOException {
        var actor=Actor.require(req);auth.rateLimit("upload",Long.toString(actor.id()),30);
        com.fasterxml.jackson.databind.JsonNode parsed;try { parsed=json.read(tags); }catch(Exception e){throw new BusinessException(ErrorCode.BAD_REQUEST);}
        BusinessException.require(parsed.isArray() && parsed.size()<=10,ErrorCode.BAD_REQUEST);var list=new ArrayList<String>();
        for(var node:parsed) { BusinessException.require(node.isTextual(),ErrorCode.BAD_REQUEST);list.add(node.asText()); }
        return ApiResponse.ok(service.view(service.upload(actor,spaceId,file.getBytes(),title,category,list,"UPLOAD")));
    }
    @Operation(summary="图片详情；待审核图片只允许上传者/管理员读取") @GetMapping("/{id}")
    ApiResponse<Map<String,Object>> detail(@PathVariable long id,HttpServletRequest req) { return ApiResponse.ok(service.view(access.readPicture(Actor.optional(req),id))); }
    @Operation(summary="鉴权读取原图；COS 存储使用私有桶，禁止绕过权限") @GetMapping("/{id}/content")
    ResponseEntity<byte[]> content(@PathVariable long id,HttpServletRequest req) {
        var p=access.readPicture(Actor.optional(req),id);return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
            .header("Cache-Control","private, no-store").header("Content-Disposition","inline; filename=\"picture-"+id+".png\"").body(storage.read(p.getObjectKey()));
    }
    @Operation(summary="基于版本 CAS 编辑元数据；公共编辑后重新审核") @PutMapping("/{id}")
    ApiResponse<Map<String,Object>> edit(@PathVariable long id,@Valid @RequestBody Edit b,HttpServletRequest req) { return ApiResponse.ok(service.view(service.update(Actor.require(req),id,new PictureService.Metadata(b.title,b.category,b.tags,b.version)))); }
    @Operation(summary="裁剪/旋转；重新核算配额并进行版本冲突校验") @PostMapping("/{id}/transform")
    ApiResponse<Map<String,Object>> transform(@PathVariable long id,@Valid @RequestBody Transform b,HttpServletRequest req) { return ApiResponse.ok(service.view(service.transform(Actor.require(req),id,b.version,b.x,b.y,b.width,b.height,b.rotation))); }
    @Operation(summary="删除图片；配额与删除同事务，对象清理由持久化队列完成") @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable long id,@RequestParam int version,HttpServletRequest req) { service.delete(Actor.require(req),id,version);return ApiResponse.ok(null); }
    @Operation(summary="最多 50 张同空间图片批量编辑/删除，任意权限/版本失败全部回滚") @PostMapping("/batch")
    ApiResponse<List<Map<String,Object>>> batch(@Valid @RequestBody Batch body,HttpServletRequest req) { return ApiResponse.ok(service.batch(Actor.require(req),body.ids,body.versions,body.remove,body.category,body.tags)); }
    @Operation(summary="管理员查看公共图库审核队列") @GetMapping("/review-queue")
    ApiResponse<Object> queue(@RequestParam(defaultValue="1") int page,HttpServletRequest req) {
        BusinessException.require(Actor.require(req).admin(),ErrorCode.FORBIDDEN);BusinessException.require(page>=1 && page<=10000,ErrorCode.BAD_REQUEST);
        var rows=pictures.selectPage(new Page<>(page,24),Wrappers.<Picture>lambdaQuery().eq(Picture::getReviewStatus,"PENDING").orderByAsc(Picture::getCreatedAt,Picture::getId));
        return ApiResponse.ok(Map.of("records",rows.getRecords().stream().map(service::view).toList(),"total",rows.getTotal()));
    }
    @Operation(summary="管理员审核公开图片") @PostMapping("/{id}/review")
    ApiResponse<Map<String,Object>> review(@PathVariable long id,@Valid @RequestBody Review b,HttpServletRequest req) { return ApiResponse.ok(service.view(service.review(Actor.require(req),id,b.version,b.status,b.reason))); }
}
