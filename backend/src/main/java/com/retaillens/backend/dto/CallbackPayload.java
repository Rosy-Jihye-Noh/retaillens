package com.retaillens.backend.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CallbackPayload {
    private UUID jobId;
    private String status;              // DONE / FAILED
    private List<VisitorResult> visitors;
    private Map<String, Object> heatmap;
    private String errorMessage;
}