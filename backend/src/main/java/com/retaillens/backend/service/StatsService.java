package com.retaillens.backend.service;

import com.retaillens.backend.dto.StatsResponse;
import com.retaillens.backend.entity.Visitor;
import com.retaillens.backend.repository.VisitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import com.retaillens.backend.repository.JobRepository;
import com.retaillens.backend.entity.Job;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class StatsService {
    private final VisitorRepository visitorRepo;
    private final JobRepository jobRepo;

    public StatsResponse aggregate(UUID jobId) {
        List<Visitor> visitors = (jobId == null)
                ? visitorRepo.findAll()
                : visitorRepo.findByJobId(jobId);

        if (visitors.isEmpty()) {
            return StatsResponse.builder()
                    .ageDistribution(Map.of())
                    .genderDistribution(Map.of())
                    .build();
        }

        long count = visitors.size();
        double avgDwell = visitors.stream()
                .filter(v -> v.getDwellSec() != null)
                .mapToDouble(v -> v.getDwellSec().doubleValue())
                .average().orElse(0);

        long purchased = visitors.stream()
                .filter(v -> Boolean.TRUE.equals(v.getEstimatedPurchase())).count();
        long checkoutVisit = visitors.stream()
                .filter(v -> Boolean.TRUE.equals(v.getVisitedCheckout())).count();
        double avgCkDwell = visitors.stream()
                .filter(v -> v.getCheckoutDwellSec() != null)
                .mapToDouble(v -> v.getCheckoutDwellSec().doubleValue())
                .average().orElse(0);

        Map<String, Long> ageDist = visitors.stream().collect(Collectors.groupingBy(
                v -> Optional.ofNullable(v.getEstimatedAgeBand()).orElse("unknown"),
                Collectors.counting()));
        Map<String, Long> genderDist = visitors.stream().collect(Collectors.groupingBy(
                v -> Optional.ofNullable(v.getEstimatedGender()).orElse("unknown"),
                Collectors.counting()));

        // 시간대별 집계 (recorded_at + enter_at_sec → 실제 시각)
        java.util.Set<UUID> jobIds = visitors.stream()
                .map(Visitor::getJobId).collect(java.util.stream.Collectors.toSet());
        Map<UUID, OffsetDateTime> recordedAtMap = jobRepo.findAllById(jobIds).stream()
                .filter(j -> j.getRecordedAt() != null)
                .collect(java.util.stream.Collectors.toMap(Job::getId, Job::getRecordedAt));

        Map<Integer, Long> hourlyV = new java.util.TreeMap<>();
        Map<Integer, Long> hourlyP = new java.util.TreeMap<>();
        for (Visitor v : visitors) {
                OffsetDateTime rec = recordedAtMap.get(v.getJobId());
                if (rec == null || v.getEnterAtSec() == null) continue;
                OffsetDateTime actual = rec.plusSeconds(v.getEnterAtSec().longValue());
                int hour = actual.atZoneSameInstant(java.time.ZoneId.of("Asia/Seoul")).getHour();
                hourlyV.merge(hour, 1L, Long::sum);
                if (Boolean.TRUE.equals(v.getEstimatedPurchase())) {
                        hourlyP.merge(hour, 1L, Long::sum);
                }
        }

        return StatsResponse.builder()
                .visitorCount(count)
                .avgDwellSec(round(avgDwell, 2))
                .estimatedConversionRate(round((double) purchased / count, 4))
                .noPurchaseCount(count - purchased)
                .checkoutVisitCount(checkoutVisit)
                .avgCheckoutDwellSec(round(avgCkDwell, 2))
                .ageDistribution(ageDist)
                .genderDistribution(genderDist)
                .hourlyVisitorCount(hourlyV)
                .hourlyPurchaseCount(hourlyP)
                .build();
    }

    private double round(double v, int d) {
        double f = Math.pow(10, d);
        return Math.round(v * f) / f;
    }
}