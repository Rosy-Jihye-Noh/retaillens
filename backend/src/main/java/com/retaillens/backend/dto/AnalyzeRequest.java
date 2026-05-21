package com.retaillens.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnalyzeRequest {
    private String jobId;       // SNAKE_CASE 설정으로 "job_id"로 자동 직렬화
    private String videoUrl;    // "video_url"
}