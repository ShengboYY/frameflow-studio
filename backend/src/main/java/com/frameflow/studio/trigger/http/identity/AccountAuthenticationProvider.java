package com.frameflow.studio.trigger.http.identity;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.frameflow.studio.cases.identity.IAuthenticateAccountCase;
import com.frameflow.studio.cases.identity.model.InvalidCredentialsException;

@Component
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class AccountAuthenticationProvider implements AuthenticationProvider {
    private final IAuthenticateAccountCase authenticate;

    public AccountAuthenticationProvider(IAuthenticateAccountCase authenticate) {
        this.authenticate = authenticate;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        try {
            var account = authenticate.authenticate(authentication.getName(),
                    (String) authentication.getCredentials());
            return UsernamePasswordAuthenticationToken.authenticated(account, null, List.of());
        } catch (InvalidCredentialsException ex) {
            throw new BadCredentialsException("Invalid credentials");
        } catch (DataAccessException ex) {
            // A dependency outage must not be reported as an incorrect password.
            throw new AuthenticationServiceException("Account store unavailable", ex);
        }
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
