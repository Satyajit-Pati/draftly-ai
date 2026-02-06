package com.sp.demo.repository;

import com.sp.demo.domain.entity.SendAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SendAttemptRepository extends JpaRepository<SendAttempt, UUID> {

  int countByDraftId(UUID draftId);
}
