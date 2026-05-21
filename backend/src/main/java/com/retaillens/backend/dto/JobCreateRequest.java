package com.retaillens.backend.dto;

import lombok.Data;
import java.time.OffsetDateTime;

@Data
public class JobCreateRequest {
    private String videoFilename;
    private String videoUrl;            // Walking Skeleton용 임시 (P1B에서 multipart로 교체)
    private OffsetDateTime recordedAt;
}