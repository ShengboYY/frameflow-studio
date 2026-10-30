package com.frameflow.studio.infrastructure.config;

import java.time.Clock;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import com.frameflow.studio.trigger.http.error.ApiRequestFilter;
import com.frameflow.studio.trigger.http.error.JsonErrors;
import com.frameflow.studio.trigger.http.identity.AccountAuthenticationProvider;
import com.frameflow.studio.trigger.http.identity.AuthenticationHttpHandlers;
import com.frameflow.studio.trigger.http.identity.AuthRequestFilter;
import com.frameflow.studio.trigger.http.identity.HeaderCsrfTokenRequestHandler;
import com.frameflow.studio.trigger.http.identity.SessionLifetimeFilter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    @Bean
    FilterRegistrationBean<ApiRequestFilter> apiRequestFilter() {
        var registration = new FilterRegistrationBean<>(new ApiRequestFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountAuthenticationProvider provider,
                                           AuthenticationHttpHandlers handlers, JsonErrors errors,
                                           Clock clock) throws Exception {
        var csrf = new HttpSessionCsrfTokenRepository();
        csrf.setHeaderName("X-CSRF-TOKEN");
        http.authenticationProvider(provider)
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new HeaderCsrfTokenRequestHandler()))
                .sessionManagement(config -> config.sessionFixation(fixation -> fixation.changeSessionId()))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf", "/api/v1/templates/**",
                                "/api/v1/demo-media/*").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/logout").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint(handlers::authenticationRequired)
                        .accessDeniedHandler(handlers::csrfRejected))
                .formLogin(config -> config.loginPage("/api/v1/auth/login").loginProcessingUrl("/api/v1/auth/login")
                        .successHandler(handlers::loginSucceeded)
                        .failureHandler(handlers::loginFailed))
                .logout(config -> config
                        .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.POST, "/api/v1/auth/logout"))
                        .addLogoutHandler(handlers::clearSessionCookie)
                        .logoutSuccessHandler(handlers::loggedOut))
                .addFilterBefore(new SessionLifetimeFilter(clock), SecurityContextHolderFilter.class)
                .addFilterBefore(new AuthRequestFilter(errors), LogoutFilter.class);
        return http.build();
    }
}
