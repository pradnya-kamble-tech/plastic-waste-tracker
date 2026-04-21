package com.plasticaudit.repository;

import com.plasticaudit.entity.AuditReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditReportRepository extends JpaRepository<AuditReport, Long> {

    List<AuditReport> findByIndustryId(Long industryId);

    List<AuditReport> findByStatus(AuditReport.ReportStatus status);

    @Query("SELECT r FROM AuditReport r JOIN FETCH r.industry LEFT JOIN FETCH r.generatedBy")
    List<AuditReport> findAll();

    @Query("SELECT r FROM AuditReport r JOIN FETCH r.industry LEFT JOIN FETCH r.generatedBy WHERE r.id = :id")
    java.util.Optional<AuditReport> findByIdWithDetails(@Param("id") Long id);

    // CO3: HQL — find reports for a specific industry ordered by date
    @Query("SELECT r FROM AuditReport r JOIN FETCH r.industry LEFT JOIN FETCH r.generatedBy WHERE r.industry.id = :industryId ORDER BY r.generatedAt DESC")
    List<AuditReport> findReportsByIndustry(@Param("industryId") Long industryId);

    // HQL — find all approved reports for SDG dashboard
    @Query("SELECT r FROM AuditReport r JOIN FETCH r.industry WHERE r.status = 'APPROVED' ORDER BY r.generatedAt DESC")
    List<AuditReport> findAllApprovedReports();

    // HQL — average reduction rate across all approved reports (SDG metric)
    @Query("SELECT AVG(r.reductionRatePercent) FROM AuditReport r WHERE r.status = 'APPROVED'")
    Double getAverageReductionRate();
}
