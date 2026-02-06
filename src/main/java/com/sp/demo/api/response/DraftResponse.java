package com.sp.demo.api.response;

import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class DraftResponse {

  private UUID id;
  private String draftText;
  private String tone;
  private String status;
  private Instant createdAt;
}

