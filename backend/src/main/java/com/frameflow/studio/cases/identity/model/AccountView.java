package com.frameflow.studio.cases.identity.model;

import java.util.UUID;
import com.frameflow.studio.domain.identity.model.entity.Account;

public record AccountView(UUID ownerId, String username, String displayName) {
    public static AccountView from(Account account) {
        return new AccountView(account.getAccountId(), account.getUsername(), account.getDisplayName());
    }
}
