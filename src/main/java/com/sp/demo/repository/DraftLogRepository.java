package com.sp.demo.repository;

import com.sp.demo.domain.entity.DraftLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DraftLogRepository extends JpaRepository<DraftLog, UUID> {
}
