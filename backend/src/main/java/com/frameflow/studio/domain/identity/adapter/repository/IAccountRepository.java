package com.frameflow.studio.domain.identity.adapter.repository;

import java.util.Optional;
import java.util.UUID;
import com.frameflow.studio.domain.identity.model.entity.Account;

public interface IAccountRepository {
    Optional<Account> findByUsername(String username);
    Optional<Account> findById(UUID accountId);
    /** Inserts a missing preset without changing an existing account or its password. */
    void insertIfAbsent(Account account);
}
