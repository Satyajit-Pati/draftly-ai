package com.sp.demo.service.workflow;

import com.sp.demo.domain.entity.Draft;
import com.sp.demo.domain.enums.DraftStatus;
import com.sp.demo.repository.DraftRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DraftRetryService {

  private final DraftRepository draftRepository;
  private final DraftWorkflowService draftWorkflowService;

  @Scheduled(fixedDelay = 15000) // dev frequency: 15s; increase later
  public void retryFailedDrafts() {

    List<DraftStatus> statuses = List.of(
        DraftStatus.APPROVED,
        DraftStatus.FAILED
    );

    List<Draft> drafts = draftRepository.findTop10ByStatusInOrderByCreatedAtAsc(statuses);

    for (Draft draft : drafts) {

      // skip ones currently being sent
      if (draft.getStatus() == DraftStatus.SENDING) {
        continue;
      }

      if (draft.getAttempts() >= draft.getMaxAttempts()) {
        continue;
      }

      try {
        draftWorkflowService.sendDraft(draft.getId());
      } catch (Exception ignored) {
        // already logged inside sendDraft — swallow to keep scheduler running
      }
    }
  }

}

