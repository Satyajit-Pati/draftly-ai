package com.sp.demo.repository;

import com.sp.demo.domain.entity.UserPreference;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {
  Optional<UserPreference> findByUserId(UUID userId);
}
