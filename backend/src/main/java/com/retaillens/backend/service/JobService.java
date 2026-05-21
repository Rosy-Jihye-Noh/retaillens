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

@Service @RequiredArgsConstructor @Slf4j
public class JobService {
    private final JobRepository jobRepo;
    private final VisitorRepository visitorRepo;
    private final RestClient restClient;
    @Value("${ai-server.url}") private String aiServerUrl;

    @Transactional
    public Job createAndDispatch(JobCreateRequest req) {
        Job job = Job.builder()
                .videoFilename(req.getVideoFilename())
                .recordedAt(req.getRecordedAt())
                .status("QUEUED").progress((short) 0)
                .build();
        job = jobRepo.save(job);

        try {
            AnalyzeRequest body = new AnalyzeRequest(
                job.getId().toString(),
                req.getVideoUrl() == null ? "" : req.getVideoUrl()
            );
            log.info("Dispatching to AI: {}", body);    // 디버깅용
            restClient.post().uri(aiServerUrl + "/analyze")
                      .contentType(MediaType.APPLICATION_JSON)
                      .body(body).retrieve().toBodilessEntity();
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