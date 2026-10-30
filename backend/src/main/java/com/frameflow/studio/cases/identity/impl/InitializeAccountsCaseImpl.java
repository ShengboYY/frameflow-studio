package com.frameflow.studio.cases.identity.impl;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.frameflow.studio.cases.identity.IInitializeAccountsCase;
import com.frameflow.studio.domain.identity.adapter.port.IPasswordVerifier;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;
import com.frameflow.studio.domain.identity.model.entity.Account;

@Service
public class InitializeAccountsCaseImpl implements IInitializeAccountsCase {
    private final IAccountRepository repository;
    private final IPasswordVerifier passwords;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public InitializeAccountsCaseImpl(IAccountRepository repository, IPasswordVerifier passwords,
                                     Clock clock, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.passwords = passwords;
        this.clock = clock;
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public void initialize(String demoAPassword, String demoBPassword) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Account initialization must start outside a transaction");
        }
        List<Account> missing = new ArrayList<>();
        boolean needsA = repository.findByUsername("demo-a").isEmpty();
        boolean needsB = repository.findByUsername("demo-b").isEmpty();
        // Validate all required secrets before any write; hashing must not hold database locks.
        if (needsA) validate(demoAPassword, "DEMO_A_PASSWORD");
        if (needsB) validate(demoBPassword, "DEMO_B_PASSWORD");
        if (needsA) missing.add(account("demo-a", "演示用户 A", demoAPassword));
        if (needsB) missing.add(account("demo-b", "演示用户 B", demoBPassword));
        if (!missing.isEmpty()) {
            transaction.executeWithoutResult(status -> missing.forEach(repository::insertIfAbsent));
        }
    }

    private Account account(String username, String displayName, String password) {
        return new Account(UUID.randomUUID(), username, displayName, passwords.encode(password), clock.instant());
    }

    private static void validate(String password, String variable) {
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < 8 || bytes > 72) {
            throw new IllegalArgumentException(variable + " is required for a missing account (8–72 UTF-8 bytes)");
        }
    }
}
