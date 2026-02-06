package com.sp.demo.domain.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "gmail_email",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "gmail_message_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GmailEmail {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "gmail_message_id", nullable = false)
  private String gmailMessageId;

  private String threadId;

  private String fromAddress;

  private String subject;

  @Column(columnDefinition = "TEXT")
  private String snippet;

  @Column(columnDefinition = "TEXT[]")
  private String[] labelIds;

  private Instant receivedAt;

  @CreationTimestamp
  private Instant fetchedAt;
}

