package io.github.zhouhuimin.gallery.web;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.auth.Actor;
import io.github.zhouhuimin.gallery.ai.AiJobService;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.AiJobMapper;
import io.github.zhouhuimin.gallery.model.AiJob;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/ai/jobs") @RequiredArgsConstructor
public class AiController {
    private final AiJobService service;private final AiJobMapper jobs;
    public record Create(@Positive long spaceId,@NotBlank @Size(max=1500) String prompt,@NotBlank @Size(max=100) String title) {}
    @Operation(summary="持久化绘图任务；Idempotency-Key 防止重复请求，demo 模式不调用模型") @PostMapping
    ApiResponse<Object> create(@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Create b,HttpServletRequest req) {
        BusinessException.require(key.matches("[a-zA-Z0-9_-]{8,64}"),ErrorCode.BAD_REQUEST);return ApiResponse.ok(service.view(service.create(Actor.require(req),b.spaceId,b.prompt,b.title,key)));
    }
    @Operation(summary="查询自己的任务状态及归档图片 ID") @GetMapping("/{id}")
    ApiResponse<Object> get(@PathVariable String id,HttpServletRequest req) { return ApiResponse.ok(service.view(service.get(Actor.require(req),id))); }
    @Operation(summary="当前用户最近 50 个绘图任务") @GetMapping
    ApiResponse<Object> mine(HttpServletRequest req) { return ApiResponse.ok(jobs.selectList(Wrappers.<AiJob>lambdaQuery().eq(AiJob::getUserId,Actor.require(req).id()).orderByDesc(AiJob::getCreatedAt).last("LIMIT 50")).stream().map(service::view).toList()); }
}
