package com.sp.demo.domain.entity;

import com.sp.demo.domain.enums.Actor;
import com.sp.demo.domain.enums.DraftAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "draft_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DraftLog {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "draft_id", nullable = false)
  private Draft draft;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Actor actor;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private DraftAction action;

  @JdbcTypeCode(SqlTypes.JSON)
  private String meta;
  // store extra info like error, oldText/newText etc.

  @CreationTimestamp
  private Instant createdAt;
}

