package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.MetaConnection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MetaConnectionRepository extends JpaRepository<MetaConnection, String> {
    List<MetaConnection> findByWorkspaceId(String workspaceId);
    Optional<MetaConnection> findByWorkspaceIdAndMetaAdAccountId(String workspaceId, String metaAdAccountId);
    List<MetaConnection> findByTokenStatus(String tokenStatus);
    List<MetaConnection> findByAutoManageEnabledTrue();
    Optional<MetaConnection> findFirstByWorkspaceIdAndTokenStatus(String workspaceId, String tokenStatus);
    Optional<MetaConnection> findFirstByMetaPageIdAndTokenStatus(String metaPageId, String tokenStatus);
    Optional<MetaConnection> findByIdAndWorkspaceId(String id, String workspaceId);

    default List<MetaConnection> findByAccountId(String accountId) {
        return findByWorkspaceId(accountId);
    }
    default Optional<MetaConnection> findByAccountIdAndMetaAdAccountId(String accountId, String metaAdAccountId) {
        return findByWorkspaceIdAndMetaAdAccountId(accountId, metaAdAccountId);
    }
    default Optional<MetaConnection> findFirstByAccountIdAndTokenStatus(String accountId, String tokenStatus) {
        return findFirstByWorkspaceIdAndTokenStatus(accountId, tokenStatus);
    }
}
