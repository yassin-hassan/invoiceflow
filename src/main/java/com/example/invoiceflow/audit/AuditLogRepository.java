package com.example.invoiceflow.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByActorEmailOrderByOccurredAtDesc(String actorEmail);

    @Modifying
    @Query("delete from AuditLog a where a.actorEmail = :email")
    int deleteByActorEmail(String email);
}
