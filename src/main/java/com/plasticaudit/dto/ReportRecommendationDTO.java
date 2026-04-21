package com.plasticaudit.dto;

import com.plasticaudit.entity.AuditReport;

import java.util.List;

/**
 * ReportRecommendationDTO — Transfer object pairing an AuditReport with
 * its computed list of recommendations.
 *
 * Avoids passing a Map to Thymeleaf and keeps the template clean with
 * simple dot-access: dto.report and dto.recommendations
 */
public class ReportRecommendationDTO {

    private final AuditReport report;
    private final List<String> recommendations;

    public ReportRecommendationDTO(AuditReport report, List<String> recommendations) {
        this.report = report;
        this.recommendations = recommendations;
    }

    public AuditReport getReport() {
        return report;
    }

    public List<String> getRecommendations() {
        return recommendations;
    }

    /**
     * Convenience: true when any recommendation flags a problem (not the all-ok
     * message)
     */
    public boolean hasWarnings() {
        return recommendations.stream().noneMatch(r -> r.startsWith("✅"));
    }
}
