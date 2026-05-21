package com.retaillens.backend.service;

import com.retaillens.backend.dto.*;
import com.retaillens.backend.entity.*;
import com.retaillens.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.*;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;

@Service @RequiredArgsConstructor @Slf4j
public class JobService {
    private final JobRepository jobRepo;
    private final VisitorRepository visitorRepo;
    private final RestClient restClient;
    @Value("${ai-server.url}") private String aiServerUrl;

    @Transactional
public Job createAndDispatch(MultipartFile video, OffsetDateTime recordedAt) {
    Job job = Job.builder()
            .videoFilename(video.getOriginalFilename())
            .videoSizeByte(video.getSize())
            .recordedAt(recordedAt)
            .status("QUEUED").progress((short) 0)
            .build();
    job = jobRepo.save(job);

    try {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("job_id", job.getId().toString());
        ByteArrayResource res = new ByteArrayResource(video.getBytes()) {
            @Override public String getFilename() { return video.getOriginalFilename(); }
        };
        parts.add("video", res);

        restClient.post().uri(aiServerUrl + "/analyze")
                  .contentType(MediaType.MULTIPART_FORM_DATA)
                  .body(parts).retrieve().toBodilessEntity();
        job.setStatus("RUNNING");
        job.setStartedAt(OffsetDateTime.now());
        log.info("AI server dispatched: jobId={}", job.getId());
    } catch (Exception e) {
        job.setStatus("FAILED");
        job.setErrorMessage("AI dispatch failed: " + e.getMessage());
        log.error("AI server call failed", e);
    }
    return jobRepo.save(job);
    }

    @Transactional
    public void handleCallback(CallbackPayload p) {
        Job job = jobRepo.findById(p.getJobId())
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + p.getJobId()));
        job.setStatus(p.getStatus());
        job.setFinishedAt(OffsetDateTime.now());
        job.setProgress((short) 100);
        if (p.getHeatmap() != null) {
            job.setHeatmap(p.getHeatmap());
        }
        if ("FAILED".equals(p.getStatus())) job.setErrorMessage(p.getErrorMessage());
        jobRepo.save(job);

        if (p.getVisitors() != null) {
            List<Visitor> list = p.getVisitors().stream().map(v -> Visitor.builder()
                .jobId(p.getJobId()).visitorId(v.getVisitorId())
                .estimatedAgeBand(v.getEstimatedAgeBand()).estimatedGender(v.getEstimatedGender())
                .enterAtSec(v.getEnterAtSec()).exitAtSec(v.getExitAtSec()).dwellSec(v.getDwellSec())
                .visitedCheckout(Boolean.TRUE.equals(v.getVisitedCheckout()))
                .checkoutDwellSec(v.getCheckoutDwellSec())
                .estimatedPurchase(Boolean.TRUE.equals(v.getEstimatedPurchase()))
                .trajectory(v.getTrajectory() == null ? new ArrayList<>() : v.getTrajectory())
                .build()).toList();
            visitorRepo.saveAll(list);
        }
        log.info("Callback processed: jobId={} visitors={}", p.getJobId(),
                p.getVisitors() == null ? 0 : p.getVisitors().size());
    }
}