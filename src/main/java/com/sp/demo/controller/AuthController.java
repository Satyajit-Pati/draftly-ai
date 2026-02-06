package com.sp.demo.controller;

import com.sp.demo.domain.entity.OAuthToken;
import com.sp.demo.domain.entity.User;
import com.sp.demo.domain.enums.AuthProvider;
import com.sp.demo.repository.OAuthTokenRepository;
import com.sp.demo.repository.UserRepository;
import com.sp.demo.service.TokenCryptoService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

  private final OAuthTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final OAuth2AuthorizedClientService clientService;
  private final TokenCryptoService tokenCryptoService;

  @GetMapping("/success")
  public String success(OAuth2AuthenticationToken authentication) {

    OAuth2AuthorizedClient client =
        clientService.loadAuthorizedClient(
            authentication.getAuthorizedClientRegistrationId(),
            authentication.getName()
        );

    String accessToken =
        client.getAccessToken().getTokenValue();

    OAuth2RefreshToken refreshTokenObj = client.getRefreshToken();
    String refreshToken = refreshTokenObj != null ? refreshTokenObj.getTokenValue() : null;

    String email =
        authentication.getPrincipal()
            .getAttribute("email");

    User user = userRepository
        .findByEmail(email)
        .orElseGet(() ->
            userRepository.save(
                User.builder()
                    .email(email)
                    .build()
            )
        );

    OAuthToken token = OAuthToken.builder()
        .user(user)
        .provider(AuthProvider.GOOGLE)
        .accessToken(accessToken)
        .refreshTokenEncrypted(tokenCryptoService.encrypt(refreshToken))
        .expiresAt(client.getAccessToken().getExpiresAt())
        .build();

    tokenRepository.save(token);

    return "OAuth login successful for " + email;
  }

  @PostMapping("/logout")
  public String logout(OAuth2AuthenticationToken authentication) {
    String email = authentication.getPrincipal().getAttribute("email");

    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    tokenRepository.findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)
        .ifPresent(token -> {
          token.setRevokedAt(Instant.now());
          tokenRepository.save(token);
        });

    return "Logged out";
  }
}

