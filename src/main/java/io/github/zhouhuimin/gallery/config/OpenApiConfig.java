package io.github.zhouhuimin.gallery.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI docs() { return new OpenAPI().info(new Info().title("FrameHub 企业级 AI 图片管理 API").version("1.0.0").description("公开图库、隔离空间、团队协作与持久化绘图任务"))
        .components(new Components().addSecuritySchemes("BearerAuth",new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
        .addSecurityItem(new SecurityRequirement().addList("BearerAuth")); }
}
