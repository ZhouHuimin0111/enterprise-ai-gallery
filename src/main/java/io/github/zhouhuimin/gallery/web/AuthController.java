package io.github.zhouhuimin.gallery.web;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;
    public record Credentials(@NotBlank @Pattern(regexp="[a-zA-Z0-9_]{3,32}") String username,@NotBlank @Size(min=8,max=64) String password) {}
    public record Registration(@NotBlank @Pattern(regexp="[a-zA-Z0-9_]{3,32}") String username,@NotBlank @Size(min=8,max=64) String password,@NotBlank @Size(max=60) String displayName) {}
    @Operation(summary="注册普通用户；无法自行设置管理员角色") @PostMapping("/register")
    ApiResponse<Actor> register(@Valid @RequestBody Registration body) {
        BusinessException.require(body.password().getBytes(StandardCharsets.UTF_8).length<=72,ErrorCode.BAD_REQUEST);
        return ApiResponse.ok(AuthService.actor(auth.register(body.username(),body.password(),body.displayName())));
    }
    @Operation(summary="登录；获取 Redis 共享 Token，30 分钟滑动过期") @PostMapping("/login")
    ApiResponse<Map<String,Object>> login(@Valid @RequestBody Credentials body) { BusinessException.require(body.password().getBytes(StandardCharsets.UTF_8).length<=72,ErrorCode.BAD_REQUEST);return ApiResponse.ok(auth.login(body.username(),body.password())); }
    @Operation(summary="当前用户") @GetMapping("/me") ApiResponse<Actor> me(HttpServletRequest req) { return ApiResponse.ok(Actor.require(req)); }
    @Operation(summary="使共享 Token 立即失效") @PostMapping("/logout") ApiResponse<Void> logout(HttpServletRequest req) { Actor.require(req);auth.logout(req.getHeader("Authorization").substring(7));return ApiResponse.ok(null); }
}
