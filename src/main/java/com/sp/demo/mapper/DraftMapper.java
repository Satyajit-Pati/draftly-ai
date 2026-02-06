package com.sp.demo.mapper;

import com.sp.demo.api.response.DraftResponse;
import com.sp.demo.domain.entity.Draft;

public class DraftMapper {

  private DraftResponse mapToResponse(Draft draft) {
    return DraftResponse.builder()
        .id(draft.getId())
        .draftText(draft.getDraftText())
        .tone(draft.getTone())
        .status(draft.getStatus().name())
        .createdAt(draft.getCreatedAt())
        .build();
  }
}
