package com.sp.demo.controller;

import com.sp.demo.api.response.GmailEmailResponse;
import com.sp.demo.domain.entity.GmailEmail;
import com.sp.demo.domain.entity.OAuthToken;
import com.sp.demo.domain.entity.User;
import com.sp.demo.domain.enums.AuthProvider;
import com.sp.demo.external.gmail.GmailClient;
import com.sp.demo.repository.GmailEmailRepository;
import com.sp.demo.repository.OAuthTokenRepository;
import com.sp.demo.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/gmail")
public class GmailController {

  private final GmailClient gmailClient;
  private final OAuthTokenRepository tokenRepository;
  private final UserRepository userRepository;
  private final GmailEmailRepository gmailEmailRepository;

  @GetMapping("/unread")
  public List<GmailEmailResponse> fetchUnread(
      OAuth2AuthenticationToken authentication,
      @RequestParam(defaultValue = "10") long maxResults) throws Exception {

    String email = authentication.getPrincipal().getAttribute("email");
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    OAuthToken token = tokenRepository
        .findByUserIdAndProvider(user.getId(), AuthProvider.GOOGLE)
        .orElseThrow(() -> new RuntimeException("OAuth token not found"));

    var messages = gmailClient.fetchUnread(token, maxResults);

    Instant now = Instant.now();

    for (var m : messages) {
      gmailEmailRepository.findByUserIdAndGmailMessageId(user.getId(), m.gmailMessageId())
          .orElseGet(() -> gmailEmailRepository.save(
              GmailEmail.builder()
                  .user(user)
                  .gmailMessageId(m.gmailMessageId())
                  .threadId(m.threadId())
                  .fromAddress(m.from())
                  .subject(m.subject())
                  .snippet(m.snippet())
                  .labelIds(new String[]{"INBOX", "UNREAD"})
                  .receivedAt(now)
                  .build()
          ));
    }

    return messages.stream()
        .map(m -> GmailEmailResponse.builder()
            .gmailMessageId(m.gmailMessageId())
            .threadId(m.threadId())
            .from(m.from())
            .subject(m.subject())
            .snippet(m.snippet())
            .bodyText(m.bodyText())
            .receivedAt(now)
            .build())
        .toList();
  }
}
