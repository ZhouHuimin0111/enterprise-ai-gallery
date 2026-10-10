package io.github.zhouhuimin.gallery.web;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.service.AccessService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.util.Map;
@RestController @RequestMapping("/api/collaboration") @RequiredArgsConstructor
public class CollaborationController {
    private final AccessService access;private final AuthService auth;private final StringRedisTemplate redis;private final Jsons json;
    public record Ticket(@Positive long pictureId) {}
    @Operation(summary="签发一次性 30 秒协作票据；WebSocket URL 不携带登录 Token") @PostMapping("/ticket")
    ApiResponse<Object> ticket(@Valid @RequestBody Ticket body,HttpServletRequest req) {
        var actor=Actor.require(req);var p=access.readPicture(actor,body.pictureId);BusinessException.require("TEAM".equals(access.space(p.getSpaceId()).getType()),ErrorCode.BAD_REQUEST);
        auth.rateLimit("ws",Long.toString(actor.id()),20);String ticket=auth.randomToken();
        redis.opsForValue().set("ws:ticket:"+cn.hutool.crypto.digest.DigestUtil.sha256Hex(ticket),json.write(Map.of("userId",actor.id(),"pictureId",p.getId())),Duration.ofSeconds(30));
        return ApiResponse.ok(Map.of("ticket",ticket,"expiresInSeconds",30,"path","/ws/collaborate"));
    }
}
