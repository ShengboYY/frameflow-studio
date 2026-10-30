package com.frameflow.studio.infrastructure.adapter.repository.identity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;
import com.frameflow.studio.domain.identity.model.entity.Account;
import com.frameflow.studio.infrastructure.dao.identity.IUserAccountDao;
import com.frameflow.studio.infrastructure.dao.po.identity.UserAccountPO;

@Repository
public class AccountRepository implements IAccountRepository {
    private final IUserAccountDao dao;

    public AccountRepository(IUserAccountDao dao) {
        this.dao = dao;
    }

    @Override
    public Optional<Account> findByUsername(String username) {
        return Optional.ofNullable(dao.findByUsername(username)).map(this::restore);
    }

    @Override
    public Optional<Account> findById(UUID accountId) {
        return Optional.ofNullable(dao.findById(accountId.toString())).map(this::restore);
    }

    @Override
    public void insertIfAbsent(Account account) {
        UserAccountPO po = new UserAccountPO();
        po.setAccountId(account.getAccountId().toString());
        po.setUsername(account.getUsername());
        po.setDisplayName(account.getDisplayName());
        po.setPasswordHash(account.getPasswordHash());
        po.setCreatedAt(LocalDateTime.ofInstant(account.getCreatedAt(), ZoneOffset.UTC));
        dao.insertIfAbsent(po);
    }

    private Account restore(UserAccountPO po) {
        return new Account(UUID.fromString(po.getAccountId()), po.getUsername(), po.getDisplayName(),
                po.getPasswordHash(), po.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
