package com.frameflow.studio.cases.identity;

import com.frameflow.studio.cases.identity.model.AccountView;

public interface IAuthenticateAccountCase {
    AccountView authenticate(String username, String password);
}
