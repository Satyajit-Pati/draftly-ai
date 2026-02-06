package com.sp.demo.service.workflow;

import com.sp.demo.domain.entity.Draft;
import com.sp.demo.domain.entity.DraftLog;
import com.sp.demo.domain.entity.SendAttempt;
import com.sp.demo.domain.entity.SentMessage;
import com.sp.demo.domain.entity.User;
import com.sp.demo.domain.entity.UserPreference;
import com.sp.demo.domain.enums.Actor;
import com.sp.demo.domain.enums.AuthProvider;
import com.sp.demo.domain.enums.DraftAction;
import com.sp.demo.domain.enums.DraftStatus;
import com.sp.demo.domain.enums.SendStatus;
import com.sp.demo.external.gmail.GmailClient;
import com.sp.demo.external.google.TokenRefreshService;
import com.sp.demo.repository.DraftLogRepository;
import com.sp.demo.repository.DraftRepository;
import com.sp.demo.repository.OAuthTokenRepository;
import com.sp.demo.repository.SendAttemptRepository;
import com.sp.demo.repository.SentMessageRepository;
import com.sp.demo.repository.UserRepository;
import com.sp.demo.repository.UserPreferenceRepository;
import com.sp.demo.service.AiService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DraftWorkflowService {

  private final DraftRepository draftRepository;
  private final DraftLogRepository draftLogRepository;
  private final SendAttemptRepository sendAttemptRepository;
  private final SentMessageRepository sentMessageRepository;
  private final UserRepository userRepository;
  private final UserPreferenceRepository userPreferenceRepository;

  private final AiService aiService;
  private final GmailClient gmailClient;
  private final OAuthTokenRepository tokenRepository;
  private final TokenRefreshService tokenRefreshService;


  @Transactional
  public Draft generateDraft(UUID userId,
      String gmailMessageId,
      String threadId,
      String emailContent,
      String tone) {

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));

    UserPreference preference = userPreferenceRepository
        .findByUserId(userId)
        .orElse(null);

    String effectiveTone = tone;
    if (effectiveTone == null || effectiveTone.isBlank()) {
      effectiveTone = preference != null && preference.getDefaultTone() != null
          ? preference.getDefaultTone()
          : "formal";
    }

    String reply = aiService.generateReply(emailContent, effectiveTone);

    if (preference != null && preference.getSignature() != null && !preference.getSignature().isBlank()) {
      reply = reply + "\n\n" + preference.getSignature();
    }

    Draft draft = Draft.builder()
        .user(user)
        .gmailMessageId(gmailMessageId)
        .threadId(threadId)
        .tone(effectiveTone)
        .draftText(reply)
        .status(DraftStatus.PENDING)
        .attempts(0)
        .maxAttempts(3)
        .build();

    draftRepository.save(draft);

    DraftLog log = DraftLog.builder()
        .draft(draft)
        .actor(Actor.SYSTEM)
        .action(DraftAction.GENERATED)
        .meta("{}")
        .build();

    draftLogRepository.save(log);

    return draft;
  }

  @Transactional(readOnly = true)
  public List<Draft> listDrafts(UUID userId, List<DraftStatus> statuses) {
    if (statuses == null || statuses.isEmpty()) {
      return draftRepository.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }
    return draftRepository.findTop50ByUserIdAndStatusInOrderByCreatedAtDesc(userId, statuses);
  }

  @Transactional(readOnly = true)
  public Draft getDraft(UUID draftId) {
    return draftRepository.findById(draftId)
        .orElseThrow(() -> new RuntimeException("Draft not found"));
  }

  @Transactional
  public Draft editDraft(UUID draftId, String draftText) {
    Draft draft = draftRepository.findById(draftId)
        .orElseThrow(() -> new RuntimeException("Draft not found"));

    if (draft.getStatus() != DraftStatus.PENDING &&
        draft.getStatus() != DraftStatus.EDITED) {
      throw new IllegalStateException("Only pending drafts can be edited");
    }

    draft.setDraftText(draftText);
    draft.setStatus(DraftStatus.EDITED);
    draftRepository.save(draft);

    draftLogRepository.save(
        DraftLog.builder()
            .draft(draft)
            .actor(Actor.USER)
            .action(DraftAction.EDITED)
            .meta("{}")
            .build()
    );

    return draft;
  }

  @Transactional
  public void rejectDraft(UUID draftId) {
    Draft draft = draftRepository.findById(draftId)
        .orElseThrow(() -> new RuntimeException("Draft not found"));

    if (draft.getStatus() == DraftStatus.SENT) {
      return;
    }

    draft.setStatus(DraftStatus.REJECTED);
    draft.setRejectedAt(Instant.now());
    draftRepository.save(draft);

    draftLogRepository.save(
        DraftLog.builder()
            .draft(draft)
            .actor(Actor.USER)
            .action(DraftAction.REJECTED)
            .meta("{}")
            .build()
    );
  }

  @Transactional
  public void approveDraft(UUID draftId) {

    Draft draft = draftRepository.findById(draftId)
        .orElseThrow(() -> new RuntimeException("Draft not found"));

    if (sentMessageRepository.existsByDraftId(draftId)) {
      return; // already sent → stop
    }


    if (draft.getStatus() != DraftStatus.PENDING &&
        draft.getStatus() != DraftStatus.EDITED) {

      throw new IllegalStateException(
          "Only pending drafts can be approved");
    }

    draft.setStatus(DraftStatus.APPROVED);
    draft.setApprovedAt(Instant.now());

    DraftLog log = DraftLog.builder()
        .draft(draft)
        .actor(Actor.USER)
        .action(DraftAction.APPROVED)
        .meta("{}")
        .build();

    draftLogRepository.save(log);
  }

  @Transactional
  public void sendDraft(UUID draftId) {

    Draft draft = draftRepository.findById(draftId)
        .orElseThrow(() -> new RuntimeException("Draft not found"));

    // Idempotency guard — check if already recorded as sent
    if (sentMessageRepository.existsByDraftId(draftId)) {
      // someone else already recorded the send; ensure status reflects it
      draft.setStatus(DraftStatus.SENT);
      draftRepository.save(draft);
      return;
    }

    // Only APPROVED or FAILED (retry) are eligible
    if (draft.getStatus() != DraftStatus.APPROVED &&
        draft.getStatus() != DraftStatus.FAILED) {
      throw new RuntimeException("Draft not eligible for sending");
    }

    // mark sending early to avoid duplicate parallel sends
    draft.setStatus(DraftStatus.SENDING);
    draft.setAttempts(draft.getAttempts() + 1);
    draftRepository.save(draft);

    draftLogRepository.save(
        DraftLog.builder()
            .draft(draft)
            .actor(Actor.SYSTEM)
            .action(DraftAction.SEND_STARTED)
            .meta("{}")
            .build()
    );

    int attemptNo = sendAttemptRepository.countByDraftId(draftId) + 1;

    SendAttempt attempt = SendAttempt.builder()
        .draft(draft)
        .attemptNo(attemptNo)
        .status(SendStatus.STARTED)
        .startedAt(Instant.now())
        .build();

    sendAttemptRepository.save(attempt);

    try {
      var token = tokenRepository
          .findByUserIdAndProvider(
              draft.getUser().getId(),
              AuthProvider.GOOGLE)
          .orElseThrow(() -> new RuntimeException("OAuth token not found"));

      // ensure valid token (refresh if needed)
      token = tokenRefreshService.ensureValidToken(token);

      String gmailMessageId;
      if (draft.getGmailMessageId() != null && !draft.getGmailMessageId().isBlank()) {
        GmailClient.GmailMessageDetails original =
            gmailClient.getMessageDetails(token, draft.getGmailMessageId());
        gmailMessageId = gmailClient.sendReply(token, original, draft.getDraftText());
      } else {
        gmailMessageId = gmailClient.sendEmail(
            token,
            draft.getUser().getEmail(),
            "Draftly Reply",
            draft.getDraftText()
        );
      }

      attempt.setStatus(SendStatus.SUCCEEDED);
      attempt.setFinishedAt(Instant.now());
      sendAttemptRepository.save(attempt);

      // Save sent_message BEFORE updating draft to ensure idempotency if crash happens
      sentMessageRepository.save(
          SentMessage.builder()
              .draft(draft)
              .gmailMessageId(gmailMessageId)
              .gmailThreadId(draft.getThreadId())
              .build()
      );

      draft.setStatus(DraftStatus.SENT);
      draft.setSentAt(Instant.now());
      draftRepository.save(draft);

      draftLogRepository.save(
          DraftLog.builder()
              .draft(draft)
              .actor(Actor.SYSTEM)
              .action(DraftAction.SENT)
              .meta("{}")
              .build()
      );

    } catch (Exception e) {

      if (e.getMessage() != null &&
          e.getMessage().contains("invalid_grant")) {

        var token = tokenRepository
            .findByUserIdAndProvider(
                draft.getUser().getId(),
                AuthProvider.GOOGLE)
            .orElseThrow();

        token.setRevokedAt(Instant.now());
        tokenRepository.save(token);
      }

      attempt.setStatus(SendStatus.FAILED);
      attempt.setErrorMessage(e.getMessage());
      attempt.setFinishedAt(Instant.now());
      sendAttemptRepository.save(attempt);

      draft.setStatus(DraftStatus.FAILED);
      draft.setLastError(e.getMessage());
      draftRepository.save(draft);

      draftLogRepository.save(
          DraftLog.builder()
              .draft(draft)
              .actor(Actor.SYSTEM)
              .action(DraftAction.FAILED)
              .meta("{\"error\":\"" + e.getMessage() + "\"}")
              .build()
      );

      throw new RuntimeException("Email sending failed", e);
    }
  }

}
