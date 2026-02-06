package com.sp.demo.repository;

import com.sp.demo.domain.entity.OAuthToken;
import com.sp.demo.domain.enums.AuthProvider;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, UUID> {

  Optional<OAuthToken> findByUserIdAndProvider(UUID userId, AuthProvider provider);
}
