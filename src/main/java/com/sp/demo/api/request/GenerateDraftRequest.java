package com.sp.demo.api.request;

import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GenerateDraftRequest {

  private UUID userId;
  private String gmailMessageId;
  private String threadId;
  private String emailContent;
  private String tone;
}

