package com.retaillens.backend.repository;

import com.retaillens.backend.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {}