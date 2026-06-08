package com.chubby.dolphin.repository;

import com.chubby.dolphin.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, String> {
}
