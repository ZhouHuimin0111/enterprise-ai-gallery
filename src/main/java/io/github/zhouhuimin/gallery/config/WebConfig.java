package io.github.zhouhuimin.gallery.config;
import io.github.zhouhuimin.gallery.auth.AuthService;
import io.github.zhouhuimin.gallery.common.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.*;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Value("${app.hide-api-docs}") private boolean hideDocs;
    @Value("${springdoc.api-docs.enabled}") private boolean enableDocs;
    private final AuthService auth; private final String[] origins;private final String instance;
    public WebConfig(AuthService auth,@Value("${app.allowed-origins}") String origins,@Value("${app.instance-id}") String instance) { this.auth=auth;this.origins=Arrays.stream(origins.split(",")).map(String::trim).toArray(String[]::new);this.instance=instance; }
    @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("Authorization","Content-Type","Idempotency-Key").maxAge(600); }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                if(hideDocs || !enableDocs) { response.setStatus(404);return false; }return true;
            }
        }).addPathPatterns("/doc.html","/v3/api-docs/**","/swagger-ui/**","/webjars/**");
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Cache-Control","no-store");
                response.setHeader("X-App-Instance",instance);
                String header=request.getHeader("Authorization");
                if(header!=null && !"OPTIONS".equals(request.getMethod())) {
                    var actor=auth.authenticate(header.startsWith("Bearer ")?header.substring(7):null);
                    if(actor==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);request.setAttribute("actor",actor);
                }
                return true;
            }
        }).addPathPatterns("/api/**");
    }
}
