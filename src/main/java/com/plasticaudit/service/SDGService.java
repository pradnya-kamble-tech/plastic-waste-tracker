package com.plasticaudit.service;

import com.plasticaudit.repository.WasteEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * SDGService — Computes SDG 12 & 14 impact metrics from aggregated waste data.
 *
 * SDG 12 (Responsible Consumption): measured by reductionRate (%) =
 * (recycled + eliminated) / generated × 100
 *
 * SDG 14 (Life Below Water): measured by recyclingRatio (%) =
 * recycled / generated × 100 — estimates plastic diverted from waterways
 *
 * environmentalScore (0–100): composite score blending both SDG metrics,
 * giving 50% weight to reductionRate and 50% weight to recyclingRatio,
 * capped at 100.
 */
@Service
@Transactional(readOnly = true)
public class SDGService {

    private static final Logger log = LoggerFactory.getLogger(SDGService.class);

    // Conceptual SDG 12 target: 80% reduction rate — industry benchmark
    public static final double SDG12_TARGET_RATE = 80.0;

    // Conceptual SDG 14 target: 50% recycling ratio — ocean plastic prevention
    public static final double SDG14_TARGET_RATE = 50.0;

    @Autowired
    private WasteEntryRepository wasteEntryRepository;

    /**
     * Computes and returns the full SDG impact metrics from all waste entries.
     *
     * @return SDGMetrics containing SDG 12 score, SDG 14 score, and environmental
     *         score
     */
    public SDGMetrics computeGlobalMetrics() {
        log.info("[SDGService] Computing global SDG impact metrics");

        List<Object[]> rawMetrics = wasteEntryRepository.getGlobalSdgMetrics();

        double generated = 0;
        double recycled = 0;
        double eliminated = 0;

        if (rawMetrics != null && !rawMetrics.isEmpty()) {
            Object[] row = rawMetrics.get(0);
            generated = row[0] != null ? ((Number) row[0]).doubleValue() : 0.0;
            recycled = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
            eliminated = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
        }

        double reductionRate = computeReductionRate(generated, recycled, eliminated);
        double recyclingRatio = computeRecyclingRatio(generated, recycled);
        double envScore = computeEnvironmentalScore(reductionRate, recyclingRatio);

        // Plastic leakage estimate: unrecycled & non-eliminated portion
        double leakageKg = Math.max(0, generated - recycled - eliminated);
        double leakageRate = generated > 0 ? (leakageKg / generated) * 100.0 : 0.0;

        // SDG 12 score: how close are we to the 80% reduction target?
        double sdg12Score = Math.min(100.0, (reductionRate / SDG12_TARGET_RATE) * 100.0);

        // SDG 14 score: how close are we to the 50% recycling target?
        double sdg14Score = Math.min(100.0, (recyclingRatio / SDG14_TARGET_RATE) * 100.0);

        log.info("[SDGService] Generated={}kg | Recycled={}kg | Eliminated={}kg",
                String.format("%.2f", generated),
                String.format("%.2f", recycled),
                String.format("%.2f", eliminated));
        log.info("[SDGService] ReductionRate={}% | RecyclingRatio={}% | EnvScore={}",
                String.format("%.1f", reductionRate),
                String.format("%.1f", recyclingRatio),
                String.format("%.1f", envScore));

        return new SDGMetrics(
                generated, recycled, eliminated,
                reductionRate, recyclingRatio, envScore,
                sdg12Score, sdg14Score,
                leakageKg, leakageRate);
    }

    /**
     * Computes SDG metrics scoped to a specific industry.
     *
     * @param generated  total plastic generated (kg)
     * @param recycled   total plastic recycled (kg)
     * @param eliminated total plastic eliminated (kg)
     * @return SDGMetrics populated for this industry
     */
    public SDGMetrics computeMetricsFrom(double generated, double recycled, double eliminated) {
        double reductionRate = computeReductionRate(generated, recycled, eliminated);
        double recyclingRatio = computeRecyclingRatio(generated, recycled);
        double envScore = computeEnvironmentalScore(reductionRate, recyclingRatio);
        double leakageKg = Math.max(0, generated - recycled - eliminated);
        double leakageRate = generated > 0 ? (leakageKg / generated) * 100.0 : 0.0;
        double sdg12Score = Math.min(100.0, (reductionRate / SDG12_TARGET_RATE) * 100.0);
        double sdg14Score = Math.min(100.0, (recyclingRatio / SDG14_TARGET_RATE) * 100.0);

        return new SDGMetrics(
                generated, recycled, eliminated,
                reductionRate, recyclingRatio, envScore,
                sdg12Score, sdg14Score,
                leakageKg, leakageRate);
    }

    // ── Internal calculation helpers ─────────────────────────────────────────

    /**
     * SDG 12 KPI — Reduction Rate:
     * (Recycled + Eliminated) / Generated × 100
     */
    public double computeReductionRate(double generated, double recycled, double eliminated) {
        if (generated <= 0)
            return 0.0;
        return Math.min(100.0, ((recycled + eliminated) / generated) * 100.0);
    }

    /**
     * SDG 14 KPI — Recycling Ratio:
     * Recycled / Generated × 100 — proxy for ocean plastic prevention.
     */
    public double computeRecyclingRatio(double generated, double recycled) {
        if (generated <= 0)
            return 0.0;
        return Math.min(100.0, (recycled / generated) * 100.0);
    }

    /**
     * Environmental Score (0–100):
     * Composite score = 50% weight on reductionRate + 50% weight on recyclingRatio.
     * This represents how well an industry is performing on both SDGs
     * simultaneously.
     */
    public double computeEnvironmentalScore(double reductionRate, double recyclingRatio) {
        return Math.min(100.0, (reductionRate * 0.5) + (recyclingRatio * 0.5));
    }

    // ── Inner model class ─────────────────────────────────────────────────────

    /**
     * Immutable value object carrying all computed SDG metrics.
     */
    public static class SDGMetrics {
        private final double totalGeneratedKg;
        private final double totalRecycledKg;
        private final double totalEliminatedKg;
        private final double reductionRate; // SDG 12 primary KPI
        private final double recyclingRatio; // SDG 14 primary KPI
        private final double environmentalScore; // composite 0–100
        private final double sdg12Score; // 0–100 progress toward 80% target
        private final double sdg14Score; // 0–100 progress toward 50% target
        private final double plasticLeakageKg; // estimated plastic not captured
        private final double leakageRate; // leakage as % of generated

        public SDGMetrics(double totalGeneratedKg, double totalRecycledKg, double totalEliminatedKg,
                double reductionRate, double recyclingRatio, double environmentalScore,
                double sdg12Score, double sdg14Score,
                double plasticLeakageKg, double leakageRate) {
            this.totalGeneratedKg = totalGeneratedKg;
            this.totalRecycledKg = totalRecycledKg;
            this.totalEliminatedKg = totalEliminatedKg;
            this.reductionRate = reductionRate;
            this.recyclingRatio = recyclingRatio;
            this.environmentalScore = environmentalScore;
            this.sdg12Score = sdg12Score;
            this.sdg14Score = sdg14Score;
            this.plasticLeakageKg = plasticLeakageKg;
            this.leakageRate = leakageRate;
        }

        public double getTotalGeneratedKg() {
            return totalGeneratedKg;
        }

        public double getTotalRecycledKg() {
            return totalRecycledKg;
        }

        public double getTotalEliminatedKg() {
            return totalEliminatedKg;
        }

        public double getReductionRate() {
            return reductionRate;
        }

        public double getRecyclingRatio() {
            return recyclingRatio;
        }

        public double getEnvironmentalScore() {
            return environmentalScore;
        }

        public double getSdg12Score() {
            return sdg12Score;
        }

        public double getSdg14Score() {
            return sdg14Score;
        }

        public double getPlasticLeakageKg() {
            return plasticLeakageKg;
        }

        public double getLeakageRate() {
            return leakageRate;
        }

        /** Converts the environmental score to a human-readable grade label. */
        public String getScoreGrade() {
            if (environmentalScore >= 80)
                return "EXCELLENT";
            if (environmentalScore >= 60)
                return "GOOD";
            if (environmentalScore >= 40)
                return "FAIR";
            if (environmentalScore >= 20)
                return "POOR";
            return "CRITICAL";
        }

        /** Returns a CSS color class based on the environmental score. */
        public String getScoreColorClass() {
            if (environmentalScore >= 80)
                return "score-excellent";
            if (environmentalScore >= 60)
                return "score-good";
            if (environmentalScore >= 40)
                return "score-fair";
            if (environmentalScore >= 20)
                return "score-poor";
            return "score-critical";
        }
    }
}
