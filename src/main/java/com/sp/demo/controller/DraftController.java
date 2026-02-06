package com.sp.demo.controller;

import com.sp.demo.api.request.GenerateDraftRequest;
import com.sp.demo.api.request.EditDraftRequest;
import com.sp.demo.api.response.DraftResponse;
import com.sp.demo.domain.entity.Draft;
import com.sp.demo.domain.entity.User;
import com.sp.demo.domain.enums.DraftStatus;
import com.sp.demo.repository.UserRepository;
import com.sp.demo.service.workflow.DraftWorkflowService;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/drafts")
@RequiredArgsConstructor
public class DraftController {

  private final DraftWorkflowService draftWorkflowService;
  private final UserRepository userRepository;

  @GetMapping("/users")
  public List<User> getAllUsers() {
    return userRepository.findAll();
  }

  @GetMapping
  public List<DraftResponse> listDrafts(
      OAuth2AuthenticationToken authentication,
      @RequestParam(required = false) String statuses) {

    String email = authentication.getPrincipal().getAttribute("email");
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));

    List<DraftStatus> statusList = null;
    if (statuses != null && !statuses.isBlank()) {
      statusList = Arrays.stream(statuses.split(","))
          .map(String::trim)
          .filter(s -> !s.isBlank())
          .map(DraftStatus::valueOf)
          .toList();
    }

    return draftWorkflowService.listDrafts(user.getId(), statusList)
        .stream()
        .map(d -> DraftResponse.builder()
            .id(d.getId())
            .draftText(d.getDraftText())
            .tone(d.getTone())
            .status(d.getStatus().name())
            .createdAt(d.getCreatedAt())
            .build())
        .toList();
  }

  @GetMapping("/{draftId}")
  public DraftResponse getDraft(@PathVariable UUID draftId) {
    Draft d = draftWorkflowService.getDraft(draftId);
    return DraftResponse.builder()
        .id(d.getId())
        .draftText(d.getDraftText())
        .tone(d.getTone())
        .status(d.getStatus().name())
        .createdAt(d.getCreatedAt())
        .build();
  }

  @PostMapping("/{draftId}/edit")
  public DraftResponse editDraft(
      @PathVariable UUID draftId,
      @RequestBody EditDraftRequest request) {

    Draft d = draftWorkflowService.editDraft(draftId, request.getDraftText());
    return DraftResponse.builder()
        .id(d.getId())
        .draftText(d.getDraftText())
        .tone(d.getTone())
        .status(d.getStatus().name())
        .createdAt(d.getCreatedAt())
        .build();
  }

  @PostMapping("/{draftId}/reject")
  public ResponseEntity<Void> rejectDraft(@PathVariable UUID draftId) {
    draftWorkflowService.rejectDraft(draftId);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/generate")
  public DraftResponse generateDraft(
      @RequestBody GenerateDraftRequest request) {

    Draft draft = draftWorkflowService.generateDraft(
        request.getUserId(),
        request.getGmailMessageId(),
        request.getThreadId(),
        request.getEmailContent(),
        request.getTone()
    );

    return DraftResponse.builder()
        .id(draft.getId())
        .draftText(draft.getDraftText())
        .tone(draft.getTone())
        .status(draft.getStatus().name())
        .createdAt(draft.getCreatedAt())
        .build();
  }

  @PostMapping("/{draftId}/approve")
  public ResponseEntity<Void> approveDraft(
      @PathVariable UUID draftId) {

    draftWorkflowService.approveDraft(draftId);

    return ResponseEntity.ok().build();
  }

  @PostMapping("/{draftId}/send")
  public ResponseEntity<Void> sendDraft(
      @PathVariable UUID draftId) {

    draftWorkflowService.sendDraft(draftId);

    return ResponseEntity.ok().build();
  }


}

