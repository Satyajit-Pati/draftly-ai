package com.sp.demo.service;

import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class OpenAiService implements AiService {

  @Value("${openai.api-key}")
  private String apiKey;

  private final RestTemplate restTemplate = new RestTemplate();

  @Override
  public String generateReply(String emailContent, String tone) {

    String url = "https://api.openai.com/v1/chat/completions";

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(apiKey);
    headers.setContentType(MediaType.APPLICATION_JSON);

    String prompt =
        "Write a " + tone +
            " professional email reply to this email:\n\n"
            + emailContent;

    Map<String, Object> requestBody =
        Map.of(
            "model", "gpt-4o-mini",
            "messages", List.of(
                Map.of("role", "user",
                    "content", prompt)
            )
        );

    HttpEntity<Map<String, Object>> request =
        new HttpEntity<>(requestBody, headers);

    Map response =
        restTemplate.postForObject(url, request, Map.class);

    List choices = (List) response.get("choices");
    Map firstChoice = (Map) choices.get(0);
    Map message = (Map) firstChoice.get("message");

    return (String) message.get("content");
  }
}
