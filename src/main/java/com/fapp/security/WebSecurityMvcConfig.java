package com.fapp.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Applies {@link UserScopeInterceptor} to every user-scoped path. */
@Configuration
class WebSecurityMvcConfig implements WebMvcConfigurer {

    private final UserScopeInterceptor userScope;

    WebSecurityMvcConfig(UserScopeInterceptor userScope) {
        this.userScope = userScope;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userScope).addPathPatterns("/api/users/**");
    }
}
