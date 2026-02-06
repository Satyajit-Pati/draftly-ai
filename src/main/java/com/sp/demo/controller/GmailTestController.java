package com.sp.demo.controller;

import com.sp.demo.domain.entity.OAuthToken;
import com.sp.demo.domain.entity.User;
import com.sp.demo.domain.enums.AuthProvider;
import com.sp.demo.external.gmail.GmailClient;
import com.sp.demo.external.google.TokenRefreshService;
import com.sp.demo.repository.OAuthTokenRepository;
import com.sp.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/test")
public class GmailTestController {

  private final GmailClient gmailClient;
  private final OAuthTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final TokenRefreshService refreshService;


  @GetMapping("/send")
  public String sendTestMail() throws Exception {

    User user = userRepository
        .findByEmail("patisatyajitcare4u@gmail.com")
        .orElseThrow();

    OAuthToken token =
        tokenRepository
            .findByUserIdAndProvider(
                user.getId(),
                AuthProvider.GOOGLE
            ).orElseThrow();

    gmailClient.sendEmail(
        token,
        "sinupokemongo@gmail.com",
        "🚀 FIRST MAIL FROM BACKEND",
        "If you received this — your system works."
    );

    return "MAIL SENT";
  }

  @GetMapping("/ping")
  public String ping() {
    return "WORKING";
  }

}

