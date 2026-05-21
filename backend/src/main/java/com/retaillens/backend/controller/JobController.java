package com.retaillens.backend.controller;

import com.retaillens.backend.dto.*;
import com.retaillens.backend.entity.Job;
import com.retaillens.backend.repository.JobRepository;
import com.retaillens.backend.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Map;

@RestController @RequestMapping("/jobs") @RequiredArgsConstructor
public class JobController {
    private final JobService jobService;
    private final JobRepository jobRepo;

    @PostMapping
    public ResponseEntity<JobResponse> create(@RequestBody JobCreateRequest req) {
        Job j = jobService.createAndDispatch(req);
        return ResponseEntity.accepted().body(JobResponse.from(j));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobResponse> get(@PathVariable UUID id) {
        return jobRepo.findById(id)
                .map(j -> ResponseEntity.ok(JobResponse.from(j)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/heatmap")
    public ResponseEntity<Map<String, Object>> heatmap(@PathVariable UUID id) {
        var jobOpt = jobRepo.findById(id);
        if (jobOpt.isEmpty()) return ResponseEntity.notFound().build();
        Map<String, Object> hm = jobOpt.get().getHeatmap();
        if (hm == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(hm);
    }
}