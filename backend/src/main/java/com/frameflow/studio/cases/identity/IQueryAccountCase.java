package com.frameflow.studio.cases.identity;

import java.util.Optional;
import java.util.UUID;
import com.frameflow.studio.cases.identity.model.AccountView;

public interface IQueryAccountCase {
    Optional<AccountView> findById(UUID accountId);
}
