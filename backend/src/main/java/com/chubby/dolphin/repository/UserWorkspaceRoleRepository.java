package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.UserWorkspaceRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserWorkspaceRoleRepository extends JpaRepository<UserWorkspaceRole, String> {

    Optional<UserWorkspaceRole> findByUserIdAndWorkspaceId(String userId, String workspaceId);

    List<UserWorkspaceRole> findByUserId(String userId);

    List<UserWorkspaceRole> findByWorkspaceId(String workspaceId);

    @Query("""
            SELECT r FROM UserWorkspaceRole r
            WHERE r.user.email = :email
              AND r.workspace.id = :workspaceId
            """)
    Optional<UserWorkspaceRole> findByUserEmailAndWorkspaceId(@Param("email") String email,
                                                              @Param("workspaceId") String workspaceId);
}
