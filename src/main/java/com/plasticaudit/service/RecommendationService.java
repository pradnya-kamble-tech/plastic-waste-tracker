package com.plasticaudit.service;

import com.plasticaudit.entity.AuditReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * RecommendationService — Rule-based recommendation engine.
 *
 * Rules applied per AuditReport:
 * 1. If totalGeneratedKg > WASTE_HIGH_THRESHOLD → suggest usage reduction +
 * packaging change
 * 2. If recyclingRatioPercent < RECYCLING_LOW_THRESHOLD → suggest process
 * improvement
 * 3. If reductionRatePercent < REDUCTION_LOW_THRESHOLD → suggest elimination
 * strategy
 * 4. If all metrics are healthy → positive feedback
 *
 * Returns List<String> — ready for Thymeleaf rendering.
 * Pure logic class — no persistence, no HTTP, no Spring Security.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    /**
     * Plastic generation threshold above which usage-reduction is recommended (kg)
     */
    public static final double WASTE_HIGH_THRESHOLD = 500.0;

    /** Recycling ratio below which more recycling is recommended (%) */
    public static final double RECYCLING_LOW_THRESHOLD = 30.0;

    /** Reduction rate below which additional elimination is recommended (%) */
    public static final double REDUCTION_LOW_THRESHOLD = 40.0;

    /**
     * Generates a list of actionable recommendations for a single AuditReport.
     *
     * @param report the audit report containing waste metrics
     * @return non-null list of recommendation strings (empty = no warnings)
     */
    public List<String> generateFor(AuditReport report) {
        if (report == null) {
            log.warn("[RecommendationEngine] Null report passed — returning empty list");
            return Collections.emptyList();
        }

        double generated = nullSafe(report.getTotalGeneratedKg());
        double recyclingPct = nullSafe(report.getRecyclingRatioPercent());
        double reductionPct = nullSafe(report.getReductionRatePercent());

        log.debug("[RecommendationEngine] Evaluating report#{} | generated={} recycling={}% reduction={}%",
                report.getId(), generated, recyclingPct, reductionPct);

        List<String> recs = new ArrayList<>();

        // ── Rule 1: High plastic generation ───────────────────────────────
        if (generated > WASTE_HIGH_THRESHOLD) {
            recs.add("♻️ Reduce plastic usage — generated volume (" +
                    String.format("%.0f", generated) + " kg) exceeds safe threshold of " +
                    (int) WASTE_HIGH_THRESHOLD + " kg.");
            recs.add("🌿 Switch to biodegradable or reusable packaging to lower raw plastic input.");
        }

        // ── Rule 2: Low recycling ratio ────────────────────────────────────
        if (recyclingPct < RECYCLING_LOW_THRESHOLD) {
            recs.add("🔄 Increase recycling process — current recycling ratio (" +
                    String.format("%.1f", recyclingPct) + "%) is below the 30% target. " +
                    "Consider partnering with certified recyclers.");
        }

        // ── Rule 3: Low overall reduction rate ─────────────────────────────
        if (reductionPct < REDUCTION_LOW_THRESHOLD) {
            recs.add("📉 Overall reduction rate (" +
                    String.format("%.1f", reductionPct) + "%) is below 40%. " +
                    "Introduce elimination programs (single-use plastic bans, design changes).");
        }

        // ── Rule 4: Healthy metrics — positive reinforcement ───────────────
        if (recs.isEmpty()) {
            recs.add("✅ Great performance! Recycling and reduction targets are on track. " +
                    "Continue current practices and aim for SDG 12 & 14 excellence.");
        }

        log.info("[RecommendationEngine] Report#{} → {} recommendation(s) generated",
                report.getId(), recs.size());
        return Collections.unmodifiableList(recs);
    }

    /**
     * Generates recommendations for all reports in a batch.
     * Returns parallel List — recommendations.get(i) matches reports.get(i).
     *
     * @param reports list of AuditReports (may be null/empty)
     * @return list of recommendation lists, same size as input
     */
    public List<List<String>> generateForAll(List<AuditReport> reports) {
        if (reports == null || reports.isEmpty()) {
            return Collections.emptyList();
        }
        List<List<String>> result = new ArrayList<>(reports.size());
        for (AuditReport r : reports) {
            result.add(generateFor(r));
        }
        return result;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private double nullSafe(Double value) {
        return value == null ? 0.0 : value;
    }
}
