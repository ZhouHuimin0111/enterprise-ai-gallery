package io.github.zhouhuimin.gallery.web;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import io.github.zhouhuimin.gallery.service.*;
import io.github.zhouhuimin.gallery.storage.ObjectStore;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/spaces") @RequiredArgsConstructor
public class SpaceController {
    private final SpaceService service;private final AccessService access;private final SpaceMapper spaces;private final PictureMapper pictures;private final MaintenanceMapper maintenance;private final ObjectStore store;
    @Value("${app.ai.mode}") private String aiMode;@Value("${app.instance-id}") private String instance;
    public record Create(@NotBlank @Size(max=80) String name,@NotBlank String type) {}
    public record Rename(@NotBlank @Size(max=80) String name) {}
    public record Role(@NotBlank String role) {}
    public record Accept(@NotBlank @Size(min=43,max=43) String token) {}
    @Operation(summary="公共图库和运行模式") @GetMapping("/public")
    ApiResponse<Object> publicSpace() { var s=spaces.selectOne(Wrappers.<Space>lambdaQuery().eq(Space::getType,"PUBLIC"));return ApiResponse.ok(Map.of("space",s,"storageMode",store.mode(),"aiMode",aiMode,"instance",instance)); }
    @Operation(summary="当前用户的私有/团队空间与角色") @GetMapping
    ApiResponse<Object> mine(HttpServletRequest req) { return ApiResponse.ok(spaces.mine(Actor.require(req).id())); }
    @Operation(summary="创建私有或团队空间；私有空间每人最多一个") @PostMapping
    ApiResponse<Space> create(@Valid @RequestBody Create b,HttpServletRequest req) { return ApiResponse.ok(service.create(Actor.require(req),b.name,b.type)); }
    @Operation(summary="空间详情与自身角色") @GetMapping("/{id}")
    ApiResponse<Object> detail(@PathVariable long id,HttpServletRequest req) { var a=Actor.optional(req);var s=access.readSpace(a,id);return ApiResponse.ok(Map.of("space",s,"myRole",access.role(a,s))); }
    @Operation(summary="空间所有者重命名") @PutMapping("/{id}")
    ApiResponse<Void> rename(@PathVariable long id,@Valid @RequestBody Rename b,HttpServletRequest req) { service.rename(Actor.require(req),id,b.name);return ApiResponse.ok(null); }
    @Operation(summary="所有者删除空空间；保留含图片或 AI 历史的空间") @DeleteMapping("/{id}")
    ApiResponse<Void> delete(@PathVariable long id,HttpServletRequest req) { service.delete(Actor.require(req),id);return ApiResponse.ok(null); }
    @Operation(summary="团队成员列表") @GetMapping("/{id}/members")
    ApiResponse<Object> members(@PathVariable long id,HttpServletRequest req) { var s=access.readSpace(Actor.require(req),id);BusinessException.require("TEAM".equals(s.getType()),ErrorCode.BAD_REQUEST);return ApiResponse.ok(spaces.members(id)); }
    @Operation(summary="生成一次性 48 小时邀请；只能所有者邀请管理员") @PostMapping("/{id}/invites")
    ApiResponse<Object> invite(@PathVariable long id,@Valid @RequestBody Role b,HttpServletRequest req) { return ApiResponse.ok(service.invite(Actor.require(req),id,b.role)); }
    @Operation(summary="接受邀请；数据库锁防止同一邀请被多次消费") @PostMapping("/accept-invite")
    ApiResponse<Space> accept(@Valid @RequestBody Accept b,HttpServletRequest req) { return ApiResponse.ok(service.accept(Actor.require(req),b.token)); }
    @Operation(summary="调整成员角色；保护所有者与管理员边界") @PutMapping("/{id}/members/{userId}")
    ApiResponse<Void> role(@PathVariable long id,@PathVariable long userId,@Valid @RequestBody Role b,HttpServletRequest req) { service.member(Actor.require(req),id,userId,b.role,false);return ApiResponse.ok(null); }
    @Operation(summary="移除团队成员，后续读取和协作消息立即重新检查权限") @DeleteMapping("/{id}/members/{userId}")
    ApiResponse<Void> remove(@PathVariable long id,@PathVariable long userId,HttpServletRequest req) { service.member(Actor.require(req),id,userId,"",true);return ApiResponse.ok(null); }
    @Operation(summary="图库分类、30 天上传趋势、成员贡献和空间配额") @GetMapping("/{id}/analytics")
    ApiResponse<Object> analytics(@PathVariable long id,HttpServletRequest req) {
        var s=access.readSpace(Actor.require(req),id);if("PUBLIC".equals(s.getType())) BusinessException.require(Actor.require(req).admin(),ErrorCode.FORBIDDEN);
        return ApiResponse.ok(Map.of("space",s,"categories",pictures.categories(id),"trend",pictures.trend(id),"contributors",pictures.contributors(id)));
    }
    @Operation(summary="团队管理员读取最近 50 条操作审计") @GetMapping("/{id}/audit")
    ApiResponse<Object> audit(@PathVariable long id,HttpServletRequest req) { access.manageSpace(Actor.require(req),id);return ApiResponse.ok(maintenance.auditList(id)); }
}
