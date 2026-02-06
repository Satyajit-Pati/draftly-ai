package com.sp.demo.repository;

import com.sp.demo.domain.entity.GmailEmail;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GmailEmailRepository extends JpaRepository<GmailEmail, UUID> {

  Optional<GmailEmail> findByUserIdAndGmailMessageId(UUID userId, String gmailMessageId);

  List<GmailEmail> findTop50ByUserIdOrderByReceivedAtDesc(UUID userId);
}
