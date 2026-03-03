package com.company.flowmanagement.config;

import com.company.flowmanagement.security.EmployeePermissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering interceptors.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final EmployeePermissionInterceptor employeePermissionInterceptor;

    public WebMvcConfig(EmployeePermissionInterceptor employeePermissionInterceptor) {
        this.employeePermissionInterceptor = employeePermissionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Register employee permission interceptor for all employee routes
        registry.addInterceptor(employeePermissionInterceptor)
                .addPathPatterns("/employee/**");
    }
}
