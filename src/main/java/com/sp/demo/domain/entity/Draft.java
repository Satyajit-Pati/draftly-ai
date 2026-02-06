package com.sp.demo.domain.entity;

import com.sp.demo.domain.enums.DraftStatus;
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
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "draft")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Draft {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  // MANY drafts belong to ONE user
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "gmail_message_id")
  private String gmailMessageId;

  private String threadId;

  private String tone;

  @Column(columnDefinition = "TEXT")
  private String draftText;

  @Enumerated(EnumType.STRING)
  private DraftStatus status;

  private Integer attempts;
  private Integer maxAttempts;

  @Column(columnDefinition = "TEXT")
  private String lastError;

  @CreationTimestamp
  private Instant createdAt;
  @UpdateTimestamp
  private Instant updatedAt;

  private Instant approvedAt;

  private Instant rejectedAt;

  private Instant sentAt;
}

