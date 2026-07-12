package com.quizmaster.attempt.repository;

import com.quizmaster.attempt.entity.AttemptAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttemptAuditLogRepository extends JpaRepository<AttemptAuditLog, Long> {

    List<AttemptAuditLog> findByAttemptIdOrderByOccurredAtDesc(Long attemptId);
}
