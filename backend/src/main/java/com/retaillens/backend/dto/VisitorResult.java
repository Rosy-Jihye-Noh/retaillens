package com.retaillens.backend.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class VisitorResult {
    private Integer visitorId;
    private String estimatedAgeBand;
    private String estimatedGender;
    private BigDecimal enterAtSec;
    private BigDecimal exitAtSec;
    private BigDecimal dwellSec;
    private Boolean visitedCheckout;
    private BigDecimal checkoutDwellSec;
    private Boolean estimatedPurchase;
    private List<Map<String, Object>> trajectory;
}