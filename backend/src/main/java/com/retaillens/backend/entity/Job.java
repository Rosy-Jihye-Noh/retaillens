package com.retaillens.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.Map;

@Entity @Table(name = "jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Job {
    @Id @GeneratedValue private UUID id;
    @Column(nullable = false, length = 20) private String status;
    @Column(nullable = false) private Short progress = 0;
    @Column(name = "video_filename") private String videoFilename;
    @Column(name = "video_size_byte") private Long videoSizeByte;
    @Column(name = "video_duration_sec") private BigDecimal videoDurationSec;
    @Column(name = "recorded_at") private OffsetDateTime recordedAt;
    @Column(name = "error_message", columnDefinition = "text") private String errorMessage;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @Column(name = "started_at") private OffsetDateTime startedAt;
    @Column(name = "finished_at") private OffsetDateTime finishedAt;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "heatmap", columnDefinition = "jsonb")
    private Map<String, Object> heatmap;
}