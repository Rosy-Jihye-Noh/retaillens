package com.retaillens.backend.repository;

import com.retaillens.backend.entity.Visitor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface VisitorRepository extends JpaRepository<Visitor, Long> {
    List<Visitor> findByJobId(UUID jobId);
}