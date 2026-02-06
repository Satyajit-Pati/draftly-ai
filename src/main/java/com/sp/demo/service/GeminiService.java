package com.sp.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GeminiService implements AiService {

  @Value("${gemini.api-key}")
  private String apiKey;

  private final RestTemplate restTemplate = new RestTemplate();

  @Override
  public String generateReply(String emailContent, String tone) {

    String url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=" + apiKey;

    String prompt =
        "Write a " + tone + " email reply to this email:\n\n"
            + emailContent;

    Map<String, Object> requestBody = Map.of(
        "contents", List.of(
            Map.of(
                "parts", List.of(
                    Map.of("text", prompt)
                )
            )
        )
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, Object>> request =
        new HttpEntity<>(requestBody, headers);

    Map response =
        restTemplate.postForObject(url, request, Map.class);

    // Parse response
    List candidates = (List) response.get("candidates");
    Map first = (Map) candidates.get(0);

    Map content = (Map) first.get("content");
    List parts = (List) content.get("parts");

    Map textPart = (Map) parts.get(0);

    return (String) textPart.get("text");
  }
}

