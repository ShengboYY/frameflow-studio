package com.frameflow.studio.domain.identity.model.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Read-only account data; credentials never leave the identity use cases. */
@Getter
public final class Account {
    private final UUID accountId;
    private final String username;
    private final String displayName;
    private final String passwordHash;
    private final Instant createdAt;

    public Account(UUID accountId, String username, String displayName, String passwordHash, Instant createdAt) {
        this.accountId = accountId;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }
}
