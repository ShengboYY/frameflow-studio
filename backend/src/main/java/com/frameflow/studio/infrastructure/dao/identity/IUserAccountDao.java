package com.frameflow.studio.infrastructure.dao.identity;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.frameflow.studio.infrastructure.dao.po.identity.UserAccountPO;

@Mapper
public interface IUserAccountDao {
    UserAccountPO findByUsername(@Param("username") String username);
    UserAccountPO findById(@Param("accountId") String accountId);
    int insertIfAbsent(UserAccountPO account);
}
