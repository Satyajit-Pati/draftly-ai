package com.sp.demo.domain.entity;

import com.sp.demo.domain.enums.SendStatus;
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

@Entity
@Table(name = "send_attempt")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendAttempt {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "draft_id", nullable = false)
  private Draft draft;

  @Column(nullable = false)
  private Integer attemptNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private SendStatus status;

  private String gmailSentMessageId;

  private String errorCode;

  @Column(columnDefinition = "TEXT")
  private String errorMessage;

  private Instant startedAt;

  private Instant finishedAt;
}

