package com.frameflow.studio.infrastructure.dao.po.identity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserAccountPO {
    private String accountId;
    private String username;
    private String displayName;
    private String passwordHash;
    private LocalDateTime createdAt;
}
