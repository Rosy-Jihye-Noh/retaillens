package com.retaillens.backend.service;

import com.retaillens.backend.dto.StatsResponse;
import com.retaillens.backend.entity.Visitor;
import com.retaillens.backend.repository.VisitorRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class StatsService {
    private final VisitorRepository visitorRepo;

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

        return StatsResponse.builder()
                .visitorCount(count)
                .avgDwellSec(round(avgDwell, 2))
                .estimatedConversionRate(round((double) purchased / count, 4))
                .noPurchaseCount(count - purchased)
                .checkoutVisitCount(checkoutVisit)
                .avgCheckoutDwellSec(round(avgCkDwell, 2))
                .ageDistribution(ageDist)
                .genderDistribution(genderDist)
                .build();
    }

    private double round(double v, int d) {
        double f = Math.pow(10, d);
        return Math.round(v * f) / f;
    }
}