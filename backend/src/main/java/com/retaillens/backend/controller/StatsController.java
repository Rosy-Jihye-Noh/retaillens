package com.retaillens.backend.controller;

import com.retaillens.backend.dto.StatsResponse;
import com.retaillens.backend.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/stats") @RequiredArgsConstructor
public class StatsController {
    private final StatsService statsService;

    @GetMapping
    public StatsResponse all() {
        return statsService.aggregate(null);
    }

    @GetMapping("/{jobId}")
    public StatsResponse byJob(@PathVariable UUID jobId) {
        return statsService.aggregate(jobId);
    }
}