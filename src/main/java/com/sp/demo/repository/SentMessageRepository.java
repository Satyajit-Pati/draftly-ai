package com.sp.demo.repository;

import com.sp.demo.domain.entity.SentMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SentMessageRepository extends JpaRepository<SentMessage, UUID> {
  boolean existsByDraftId(UUID draftId);
}
