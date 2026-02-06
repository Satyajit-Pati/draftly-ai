package com.sp.demo.external.google;

import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import com.sp.demo.domain.entity.OAuthToken;
import com.sp.demo.repository.OAuthTokenRepository;
import com.sp.demo.service.TokenCryptoService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class TokenRefreshService {

  @Value("${spring.security.oauth2.client.registration.google.client-id}")
  private String clientId;

  @Value("${spring.security.oauth2.client.registration.google.client-secret}")
  private String clientSecret;


  private final OAuthTokenRepository tokenRepository;
  private final TokenCryptoService tokenCryptoService;
  private final RestTemplate restTemplate = new RestTemplate();


  public OAuthToken refreshToken(OAuthToken token) {

    String url = "https://oauth2.googleapis.com/token";

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("client_id", clientId);
    body.add("client_secret", clientSecret);
    body.add("refresh_token", tokenCryptoService.decrypt(token.getRefreshTokenEncrypted()));
    body.add("grant_type", "refresh_token");

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    HttpEntity<?> request = new HttpEntity<>(body, headers);

    Map response = restTemplate.postForObject(url, request, Map.class);

    String newAccessToken = null;
    Integer expiresIn = null;
    if (response != null) {
      newAccessToken = (String) response.get("access_token");
      Object expiresInObj = response.get("expires_in");
      if (expiresInObj instanceof Integer) {
        expiresIn = (Integer) expiresInObj;
      } else if (expiresInObj instanceof Number) {
        expiresIn = ((Number) expiresInObj).intValue();
      }
    }

    token.setAccessToken(newAccessToken);
    if (expiresIn != null) {
      token.setExpiresAt(Instant.now().plusSeconds(expiresIn));
    }
    tokenRepository.save(token);

    return token;
  }

  public OAuthToken ensureValidToken(OAuthToken token) {
    if (token == null) {
      throw new IllegalArgumentException("token is null");
    }

    if (token.getExpiresAt() == null ||
        token.getExpiresAt().isBefore(Instant.now())) {

      return refreshToken(token);
    }

    return token;
  }

}

