package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    java.util.List<User> findByRole(String role);
    java.util.List<User> findByWorkspaceId(String workspaceId);
    java.util.List<User> findByOrganizationId(String organizationId);
    long countByOrganizationId(String organizationId);
}
