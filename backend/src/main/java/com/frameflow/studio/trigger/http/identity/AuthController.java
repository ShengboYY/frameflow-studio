package com.frameflow.studio.trigger.http.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.frameflow.studio.api.identity.dto.AccountResponseDTO;
import com.frameflow.studio.api.identity.dto.CsrfResponseDTO;
import com.frameflow.studio.cases.identity.IQueryAccountCase;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.trigger.http.error.JsonErrors;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IQueryAccountCase query;
    private final JsonErrors errors;

    public AuthController(IQueryAccountCase query, JsonErrors errors) {
        this.query = query;
        this.errors = errors;
    }

    @GetMapping("/csrf")
    public CsrfResponseDTO csrf(CsrfToken token) {
        return new CsrfResponseDTO(token.getToken(), token.getHeaderName());
    }

    @GetMapping("/session")
    public AccountResponseDTO session(Authentication authentication, HttpServletRequest request,
                                      HttpServletResponse response) throws IOException {
        AccountView principal = (AccountView) authentication.getPrincipal();
        var account = query.findById(principal.ownerId());
        if (account.isEmpty()) {
            var session = request.getSession(false);
            if (session != null) session.invalidate();
            SecurityContextHolder.clearContext();
            errors.write(request, response, 401, "AUTHENTICATION_REQUIRED", "请登录后继续");
            return null;
        }
        return response(account.orElseThrow());
    }

    private AccountResponseDTO response(AccountView account) {
        return new AccountResponseDTO(account.ownerId(), account.username(), account.displayName());
    }
}
