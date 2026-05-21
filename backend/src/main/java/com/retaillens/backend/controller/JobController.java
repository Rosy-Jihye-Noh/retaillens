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
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.OffsetDateTime;

@RestController @RequestMapping("/jobs") @RequiredArgsConstructor
public class JobController {
    private final JobService jobService;
    private final JobRepository jobRepo;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<JobResponse> create(
        @RequestParam("video") MultipartFile video,
        @RequestParam(value = "recordedAt", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime recordedAt) {
    Job j = jobService.createAndDispatch(video, recordedAt);
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