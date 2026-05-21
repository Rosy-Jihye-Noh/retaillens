package com.retaillens.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity
@Table(name = "visitors",
       uniqueConstraints = @UniqueConstraint(columnNames = {"job_id", "visitor_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Visitor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "job_id", nullable = false) private UUID jobId;
    @Column(name = "visitor_id", nullable = false) private Integer visitorId;
    @Column(name = "estimated_age_band", length = 20) private String estimatedAgeBand;
    @Column(name = "estimated_gender", length = 10) private String estimatedGender;
    @Column(name = "enter_at_sec", nullable = false) private BigDecimal enterAtSec;
    @Column(name = "exit_at_sec") private BigDecimal exitAtSec;
    @Column(name = "dwell_sec") private BigDecimal dwellSec;
    @Column(name = "visited_checkout", nullable = false) private Boolean visitedCheckout = false;
    @Column(name = "checkout_dwell_sec") private BigDecimal checkoutDwellSec;
    @Column(name = "estimated_purchase", nullable = false) private Boolean estimatedPurchase = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trajectory", columnDefinition = "jsonb", nullable = false)
    private List<Map<String, Object>> trajectory = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}