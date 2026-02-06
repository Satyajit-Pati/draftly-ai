package com.sp.demo.repository;

import com.sp.demo.domain.entity.Draft;
import com.sp.demo.domain.enums.DraftStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DraftRepository extends JpaRepository<Draft, UUID> {
  List<Draft> findByStatusIn(List<DraftStatus> statusIn);
  List<Draft> findTop10ByStatusInOrderByCreatedAtAsc(List<DraftStatus> statuses);

  List<Draft> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

  List<Draft> findTop50ByUserIdAndStatusInOrderByCreatedAtDesc(UUID userId, List<DraftStatus> statuses);
}
