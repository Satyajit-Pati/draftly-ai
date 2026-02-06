package com.sp.demo.api.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class GmailEmailResponse {
  private String gmailMessageId;
  private String threadId;
  private String from;
  private String subject;
  private String snippet;
  private String bodyText;
  private Instant receivedAt;
}
