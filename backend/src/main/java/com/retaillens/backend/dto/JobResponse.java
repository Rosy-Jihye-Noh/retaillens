package com.retaillens.backend.dto;

import com.retaillens.backend.entity.Job;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder @AllArgsConstructor
public class JobResponse {
    private UUID id;
    private String status;
    private Short progress;
    private String errorMessage;
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;

    public static JobResponse from(Job j) {
        return JobResponse.builder()
                .id(j.getId()).status(j.getStatus()).progress(j.getProgress())
                .errorMessage(j.getErrorMessage()).createdAt(j.getCreatedAt())
                .startedAt(j.getStartedAt()).finishedAt(j.getFinishedAt())
                .build();
    }
}