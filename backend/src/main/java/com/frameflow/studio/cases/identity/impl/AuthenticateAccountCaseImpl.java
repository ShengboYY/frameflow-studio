package com.frameflow.studio.cases.identity.impl;

import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.identity.IAuthenticateAccountCase;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.cases.identity.model.InvalidCredentialsException;
import com.frameflow.studio.domain.identity.adapter.port.IPasswordVerifier;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;

@Service
public class AuthenticateAccountCaseImpl implements IAuthenticateAccountCase {
    private final IAccountRepository repository;
    private final IPasswordVerifier passwords;

    public AuthenticateAccountCaseImpl(IAccountRepository repository, IPasswordVerifier passwords) {
        this.repository = repository;
        this.passwords = passwords;
    }

    @Override
    public AccountView authenticate(String username, String password) {
        var account = repository.findByUsername(username);
        boolean matches = passwords.matches(password, account.map(a -> a.getPasswordHash()).orElse(null));
        if (account.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        return AccountView.from(account.orElseThrow());
    }
}
