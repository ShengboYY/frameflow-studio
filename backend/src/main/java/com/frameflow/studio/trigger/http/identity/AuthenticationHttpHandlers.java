package com.frameflow.studio.trigger.http.identity;

import java.io.IOException;
import java.time.Clock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.ResponseCookie;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import com.frameflow.studio.api.identity.dto.AccountResponseDTO;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.trigger.http.error.JsonErrors;

/** HTTP/session callbacks belong to the inbound adapter, not the composition root. */
@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AuthenticationHttpHandlers {
    private final JsonErrors errors;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final boolean secureCookie;

    public AuthenticationHttpHandlers(JsonErrors errors, ObjectMapper mapper, Clock clock,
                                      @Value("${server.servlet.session.cookie.secure}") boolean secureCookie) {
        this.errors = errors;
        this.mapper = mapper;
        this.clock = clock;
        this.secureCookie = secureCookie;
    }

    public void loginSucceeded(HttpServletRequest request, HttpServletResponse response,
                               Authentication authentication) throws IOException {
        var session = request.getSession();
        session.setMaxInactiveInterval(1800);
        session.setAttribute(SessionLifetimeFilter.STARTED_AT, clock.instant());
        session.setAttribute(SessionLifetimeFilter.LAST_ACTIVITY, clock.instant());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        AccountView account = (AccountView) authentication.getPrincipal();
        mapper.writeValue(response.getOutputStream(),
                new AccountResponseDTO(account.ownerId(), account.username(), account.displayName()));
    }

    public void loginFailed(HttpServletRequest request, HttpServletResponse response,
                            AuthenticationException exception) throws IOException {
        if (exception instanceof AuthenticationServiceException) {
            errors.write(request, response, 503, "SERVICE_UNAVAILABLE", "服务暂时不可用");
        } else {
            errors.write(request, response, 401, "INVALID_CREDENTIALS", "用户名或密码错误");
        }
    }

    public void authenticationRequired(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException exception) throws IOException {
        errors.write(request, response, 401, "AUTHENTICATION_REQUIRED", "请登录后继续");
    }

    public void csrfRejected(HttpServletRequest request, HttpServletResponse response,
                             AccessDeniedException exception) throws IOException {
        errors.write(request, response, 403, "CSRF_INVALID", "请求校验已失效，请刷新会话");
    }

    public void clearSessionCookie(HttpServletRequest request, HttpServletResponse response,
                                   Authentication authentication) {
        response.addHeader("Set-Cookie", ResponseCookie.from("FF_SESSION", "").path("/api").httpOnly(true)
                .sameSite("Lax").secure(secureCookie).maxAge(0).build().toString());
    }

    public void loggedOut(HttpServletRequest request, HttpServletResponse response,
                          Authentication authentication) {
        response.setStatus(204);
    }
}
