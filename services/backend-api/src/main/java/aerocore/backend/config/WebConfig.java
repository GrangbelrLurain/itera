package aerocore.backend.config;

import aerocore.backend.service.AdminAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** CORS (the bearer token travels in a header, not a cookie) and the admin bearer-token check. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AdminAuthService> adminAuth;

    public WebConfig(ObjectProvider<AdminAuthService> adminAuth) {
        this.adminAuth = adminAuth;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                if (HttpMethod.OPTIONS.matches(request.getMethod())) return true; // CORS preflight carries no token
                // getObject throws NoSuchBeanDefinitionException (-> 503) while the admin API is not configured
                adminAuth.getObject().verifyAuthorizationHeader(request.getHeader(HttpHeaders.AUTHORIZATION));
                return true;
            }
        }).addPathPatterns("/api/v1/admin/**").excludePathPatterns("/api/v1/admin/login");
    }
}
