package com.pela.identity.adapter.in.web;

import com.pela.identity.application.port.in.AdminAccessUseCase;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AdminAccessWebConfig implements WebMvcConfigurer {

    private final ObjectProvider<AdminAccessUseCase> adminAccess;

    public AdminAccessWebConfig(ObjectProvider<AdminAccessUseCase> adminAccess) {
        this.adminAccess = adminAccess;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AdminAccessInterceptor(adminAccess)).addPathPatterns("/v1/admin/**");
    }
}
