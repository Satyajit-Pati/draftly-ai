package com.sp.demo.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class AiServiceRouter implements AiService {

  private final ObjectProvider<GeminiService> geminiService;
  private final ObjectProvider<OpenAiService> openAiService;
  private final String provider;

  public AiServiceRouter(
      ObjectProvider<GeminiService> geminiService,
      ObjectProvider<OpenAiService> openAiService,
      @Value("${ai.provider:gemini}") String provider) {
    this.geminiService = geminiService;
    this.openAiService = openAiService;
    this.provider = provider;
  }

  @Override
  public String generateReply(String emailContent, String tone) {
    if ("openai".equalsIgnoreCase(provider)) {
      OpenAiService svc = openAiService.getIfAvailable();
      if (svc == null) {
        throw new IllegalStateException("OpenAI provider selected but OpenAiService bean is not available");
      }
      return svc.generateReply(emailContent, tone);
    }

    GeminiService svc = geminiService.getIfAvailable();
    if (svc == null) {
      throw new IllegalStateException("Gemini provider selected but GeminiService bean is not available");
    }
    return svc.generateReply(emailContent, tone);
  }
}
