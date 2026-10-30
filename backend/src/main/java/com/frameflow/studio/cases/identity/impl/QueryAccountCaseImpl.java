package com.frameflow.studio.cases.identity.impl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import com.frameflow.studio.cases.identity.IQueryAccountCase;
import com.frameflow.studio.cases.identity.model.AccountView;
import com.frameflow.studio.domain.identity.adapter.repository.IAccountRepository;

@Service
public class QueryAccountCaseImpl implements IQueryAccountCase {
    private final IAccountRepository repository;

    public QueryAccountCaseImpl(IAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<AccountView> findById(UUID accountId) {
        return repository.findById(accountId).map(AccountView::from);
    }
}
