package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {
    Optional<Subscription> findByWorkspaceId(String workspaceId);
}
