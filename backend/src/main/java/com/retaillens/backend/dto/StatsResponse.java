package com.retaillens.backend.dto;

import lombok.*;
import java.util.Map;

@Data @Builder @AllArgsConstructor @NoArgsConstructor
public class StatsResponse {
    private long visitorCount;
    private double avgDwellSec;
    private double estimatedConversionRate;
    private long noPurchaseCount;
    private long checkoutVisitCount;
    private double avgCheckoutDwellSec;
    private Map<String, Long> ageDistribution;
    private Map<String, Long> genderDistribution;
}