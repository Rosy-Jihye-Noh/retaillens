package com.retaillens.backend.controller;

import com.retaillens.backend.dto.CallbackPayload;
import com.retaillens.backend.service.JobService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/callback") @RequiredArgsConstructor
public class CallbackController {
    private final JobService jobService;

    @PostMapping
    public ResponseEntity<Void> callback(@RequestBody CallbackPayload payload) {
        jobService.handleCallback(payload);
        return ResponseEntity.ok().build();
    }
}