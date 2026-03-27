package com.example.multitenancy.config;

import com.example.multitenancy.web.interceptor.TenantInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration that registers the {@link TenantInterceptor}.
 *
 * <p>This configuration class implements {@link WebMvcConfigurer} so that we can
 * add custom interceptors to Spring MVC's handler pipeline without replacing the
 * entire web configuration that Spring Boot auto-configures.</p>
 *
 * <h2>Why exclude Actuator endpoints?</h2>
 * <p>Docker health checks and monitoring tools poll {@code /actuator/health}
 * without providing an {@code X-Tenant-ID} header. The interceptor is therefore
 * excluded from {@code /actuator/**} paths so that health probes always succeed
 * regardless of the tenant resolution result.</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * The interceptor that reads the {@code X-Tenant-ID} header and populates
     * {@link com.example.multitenancy.tenant.TenantContext}.
     */
    private final TenantInterceptor tenantInterceptor;

    /**
     * Constructs the configuration with the required interceptor dependency.
     *
     * @param tenantInterceptor the tenant resolution interceptor to register
     */
    public WebMvcConfig(TenantInterceptor tenantInterceptor) {
        this.tenantInterceptor = tenantInterceptor;
    }

    /**
     * Registers the {@link TenantInterceptor} for all paths except Actuator endpoints.
     *
     * <p>The interceptor applies to all requests ({@code /**}) but excludes
     * {@code /actuator/**} so that health checks can run without a tenant header.</p>
     *
     * @param registry the Spring MVC interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(tenantInterceptor)
                .addPathPatterns("/**")
                // Exclude Actuator endpoints from tenant resolution.
                // Health checks and metrics do not belong to any specific tenant.
                .excludePathPatterns("/actuator/**");
    }
}
