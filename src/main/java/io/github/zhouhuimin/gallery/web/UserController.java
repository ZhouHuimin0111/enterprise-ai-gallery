package io.github.zhouhuimin.gallery.web;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.User;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
@RestController @RequiredArgsConstructor
public class UserController {
    private final UserMapper users;private final AuthService auth;private final MaintenanceMapper maintenance;
    public record Profile(@NotBlank @Size(max=60) String displayName) {}
    public record Password(@NotBlank @Size(max=64) String oldPassword,@NotBlank @Size(min=8,max=64) String newPassword) {}
    public record Enabled(boolean enabled) {}
    @Operation(summary="修改自己的显示名称") @PutMapping("/api/users/me")
    ApiResponse<Actor> profile(@Valid @RequestBody Profile b,HttpServletRequest req) {
        var actor=Actor.require(req);users.update(null,Wrappers.<User>lambdaUpdate().eq(User::getId,actor.id()).set(User::getDisplayName,b.displayName.trim()));return ApiResponse.ok(AuthService.actor(users.selectById(actor.id())));
    }
    @Operation(summary="验证旧密码并改密，使所有旧会话和协作身份失效") @PostMapping("/api/users/me/password")
    ApiResponse<Void> password(@Valid @RequestBody Password b,HttpServletRequest req) {
        BusinessException.require(b.oldPassword.getBytes(StandardCharsets.UTF_8).length<=72 && b.newPassword.getBytes(StandardCharsets.UTF_8).length<=72,ErrorCode.BAD_REQUEST);
        auth.changePassword(Actor.require(req),b.oldPassword,b.newPassword);return ApiResponse.ok(null);
    }
    @Operation(summary="管理员分页查询账号，不返回密码 Hash") @GetMapping("/api/admin/users")
    ApiResponse<Object> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="") String q,HttpServletRequest req) {
        BusinessException.require(Actor.require(req).admin(),ErrorCode.FORBIDDEN);BusinessException.require(page>=1&&page<=10000&&q.length()<=32,ErrorCode.BAD_REQUEST);
        var result=users.selectPage(new Page<>(page,20),Wrappers.<User>lambdaQuery().like(!q.isBlank(),User::getUsername,q).orderByDesc(User::getId));
        return ApiResponse.ok(Map.of("records",result.getRecords().stream().map(u->Map.of("user",AuthService.actor(u),"enabled",u.getEnabled(),"createdAt",u.getCreatedAt())).toList(),"total",result.getTotal()));
    }
    @Operation(summary="管理员启用/停用普通账号；停用立即使旧会话失效") @PutMapping("/api/admin/users/{id}")
    ApiResponse<Void> enabled(@PathVariable long id,@RequestBody Enabled b,HttpServletRequest req) { enable(Actor.require(req),id,b.enabled);return ApiResponse.ok(null); }
    @Operation(summary="管理员逻辑删除普通账号（停用），保留图片/审计外键") @DeleteMapping("/api/admin/users/{id}")
    ApiResponse<Void> delete(@PathVariable long id,HttpServletRequest req) { enable(Actor.require(req),id,false);return ApiResponse.ok(null); }
    private void enable(Actor actor,long id,boolean enabled) {
        BusinessException.require(actor.admin(),ErrorCode.FORBIDDEN);var u=users.selectById(id);BusinessException.require(u!=null,ErrorCode.NOT_FOUND);
        BusinessException.require("USER".equals(u.getRole())&&!u.getUsername().equals("system"),ErrorCode.FORBIDDEN);
        users.update(null,Wrappers.<User>lambdaUpdate().eq(User::getId,id).set(User::getEnabled,enabled).setSql("auth_version=auth_version+1"));maintenance.audit(actor.id(),null,enabled?"ACCOUNT_ENABLE":"ACCOUNT_DISABLE",Long.toString(id));
    }
}
