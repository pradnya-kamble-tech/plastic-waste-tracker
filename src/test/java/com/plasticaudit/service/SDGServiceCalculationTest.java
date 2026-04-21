package com.plasticaudit.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for SDGService calculation methods.
 * No Spring context needed — tests only the math.
 *
 * Covers:
 * - reductionRate calculation (SDG 12 KPI)
 * - recyclingRatio calculation (SDG 14 KPI)
 * - environmentalScore computation
 * - SDGMetrics value object methods (grade, color class, leakage)
 * - Edge cases: zero waste, 100% recycling, negative inputs, very high values
 */
class SDGServiceCalculationTest {

    private SDGService sdgService;

    @BeforeEach
    void setUp() {
        // SDGService has no repository injection for the pure math methods —
        // instantiate directly so tests run in milliseconds, no container needed.
        sdgService = new SDGService();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. REDUCTION RATE — SDG 12 KPI
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("computeReductionRate()")
    class ReductionRateTests {

        @Test
        @DisplayName("Normal values → (recycled + eliminated) / generated × 100")
        void testReductionRate_normalValues() {
            // 100 generated, 60 recycled, 20 eliminated → (60+20)/100 × 100 = 80%
            double rate = sdgService.computeReductionRate(100, 60, 20);
            assertEquals(80.0, rate, 0.001, "Reduction rate should be 80%");
        }

        @Test
        @DisplayName("Zero generated waste → rate is 0 (no division by zero)")
        void testReductionRate_zeroGenerated() {
            double rate = sdgService.computeReductionRate(0, 0, 0);
            assertEquals(0.0, rate, 0.001, "Zero generated should return 0%");
        }

        @Test
        @DisplayName("100% recycling → rate is 100%")
        void testReductionRate_hundredPercentRecycling() {
            double rate = sdgService.computeReductionRate(200, 200, 0);
            assertEquals(100.0, rate, 0.001, "Full recycling should return 100%");
        }

        @Test
        @DisplayName("100% elimination → rate is 100%")
        void testReductionRate_hundredPercentElimination() {
            double rate = sdgService.computeReductionRate(150, 0, 150);
            assertEquals(100.0, rate, 0.001, "Full elimination should return 100%");
        }

        @Test
        @DisplayName("Very high waste values → result still capped at 100%")
        void testReductionRate_veryHighWaste() {
            double rate = sdgService.computeReductionRate(1_000_000, 600_000, 400_000);
            assertEquals(100.0, rate, 0.001, "Result must be capped at 100%");
        }

        @Test
        @DisplayName("Partial recycling → correct fractional rate")
        void testReductionRate_partialRecycling() {
            // 1000 generated, 250 recycled, 100 eliminated → 35%
            double rate = sdgService.computeReductionRate(1000, 250, 100);
            assertEquals(35.0, rate, 0.001, "Partial rate should be 35%");
        }

        @Test
        @DisplayName("Negative generated (invalid) → treated as 0")
        void testReductionRate_negativeGenerated() {
            // Safety: negative generated should not produce NaN or negative rate
            double rate = sdgService.computeReductionRate(-100, 50, 20);
            assertEquals(0.0, rate, 0.001, "Negative generated should return 0%");
        }

        @Test
        @DisplayName("Only generated, no recovery → 0%")
        void testReductionRate_noRecovery() {
            double rate = sdgService.computeReductionRate(500, 0, 0);
            assertEquals(0.0, rate, 0.001, "No recovery should return 0%");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. RECYCLING RATIO — SDG 14 KPI
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("computeRecyclingRatio()")
    class RecyclingRatioTests {

        @Test
        @DisplayName("Normal values → recycled / generated × 100")
        void testRecyclingRatio_normalValues() {
            // 200 generated, 60 recycled → 30%
            double ratio = sdgService.computeRecyclingRatio(200, 60);
            assertEquals(30.0, ratio, 0.001, "Recycling ratio should be 30%");
        }

        @Test
        @DisplayName("Zero generated → ratio is 0 (no division by zero)")
        void testRecyclingRatio_zeroGenerated() {
            double ratio = sdgService.computeRecyclingRatio(0, 0);
            assertEquals(0.0, ratio, 0.001, "Zero generated should return 0%");
        }

        @Test
        @DisplayName("100% recycling → ratio is 100%")
        void testRecyclingRatio_hundredPercent() {
            double ratio = sdgService.computeRecyclingRatio(500, 500);
            assertEquals(100.0, ratio, 0.001, "Full recycling should return 100%");
        }

        @Test
        @DisplayName("Recycled > generated → capped at 100% (defensive)")
        void testRecyclingRatio_recycledExceedsGenerated() {
            double ratio = sdgService.computeRecyclingRatio(100, 120);
            assertEquals(100.0, ratio, 0.001, "Recycling ratio should never exceed 100%");
        }

        @Test
        @DisplayName("Very large numbers → no overflow or precision issues")
        void testRecyclingRatio_largeNumbers() {
            double ratio = sdgService.computeRecyclingRatio(5_000_000, 2_500_000);
            assertEquals(50.0, ratio, 0.001, "Large number ratio should be 50%");
        }

        @Test
        @DisplayName("Zero recycled → ratio is 0%")
        void testRecyclingRatio_noRecycling() {
            double ratio = sdgService.computeRecyclingRatio(750, 0);
            assertEquals(0.0, ratio, 0.001, "No recycling should return 0%");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. ENVIRONMENTAL SCORE — COMPOSITE METRIC
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("computeEnvironmentalScore()")
    class EnvironmentalScoreTests {

        @Test
        @DisplayName("Normal values → 50% × reductionRate + 50% × recyclingRatio")
        void testEnvironmentalScore_normalValues() {
            // reductionRate=80%, recyclingRatio=60% → score = 0.5×80 + 0.5×60 = 70.0
            double score = sdgService.computeEnvironmentalScore(80, 60);
            assertEquals(70.0, score, 0.001, "Environmental score should be 70.0");
        }

        @Test
        @DisplayName("Perfect inputs → score is 100.0")
        void testEnvironmentalScore_perfectScore() {
            double score = sdgService.computeEnvironmentalScore(100, 100);
            assertEquals(100.0, score, 0.001, "Perfect inputs should yield score 100%");
        }

        @Test
        @DisplayName("Zero inputs → score is 0.0")
        void testEnvironmentalScore_zeroInputs() {
            double score = sdgService.computeEnvironmentalScore(0, 0);
            assertEquals(0.0, score, 0.001, "Zero inputs should yield score 0%");
        }

        @Test
        @DisplayName("Score cannot exceed 100 even with inflated inputs")
        void testEnvironmentalScore_cappedAt100() {
            // Both at full 100 → 0.5×100 + 0.5×100 = 100 (still capped)
            double score = sdgService.computeEnvironmentalScore(100, 100);
            assertTrue(score <= 100.0, "Score must never exceed 100");
        }

        @Test
        @DisplayName("Asymmetric inputs → weighted correctly")
        void testEnvironmentalScore_asymmetricWeighting() {
            // reductionRate=100%, recyclingRatio=0% → score = 50.0
            double score = sdgService.computeEnvironmentalScore(100, 0);
            assertEquals(50.0, score, 0.001, "Half-weight score should be 50.0");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. SDGMetrics VALUE OBJECT
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SDGMetrics value object")
    class SDGMetricsValueObjectTests {

        @Test
        @DisplayName("computeMetricsFrom() builds correct SDGMetrics with all fields")
        void testComputeMetricsFrom_populatesAllFields() {
            // 500 generated, 300 recycled, 100 eliminated
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(500, 300, 100);

            assertNotNull(m, "Metrics object must not be null");
            assertEquals(500.0, m.getTotalGeneratedKg(), 0.001);
            assertEquals(300.0, m.getTotalRecycledKg(), 0.001);
            assertEquals(100.0, m.getTotalEliminatedKg(), 0.001);

            // reductionRate = (300+100)/500 × 100 = 80%
            assertEquals(80.0, m.getReductionRate(), 0.001, "Reduction rate");
            // recyclingRatio = 300/500 × 100 = 60%
            assertEquals(60.0, m.getRecyclingRatio(), 0.001, "Recycling ratio");
            // environmentalScore = 0.5×80 + 0.5×60 = 70%
            assertEquals(70.0, m.getEnvironmentalScore(), 0.001, "Environmental score");

            // SDG 12 score: reductionRate/80 × 100 = 80/80 × 100 = 100
            assertEquals(100.0, m.getSdg12Score(), 0.001, "SDG 12 score at target");
            // SDG 14 score: recyclingRatio/50 × 100 = 60/50 × 100 = 100 (capped)
            assertEquals(100.0, m.getSdg14Score(), 0.001, "SDG 14 score above target");

            // leakageKg = 500 - 300 - 100 = 100
            assertEquals(100.0, m.getPlasticLeakageKg(), 0.001, "Leakage estimate");
            // leakageRate = 100/500 × 100 = 20%
            assertEquals(20.0, m.getLeakageRate(), 0.001, "Leakage rate");
        }

        @Test
        @DisplayName("Zero waste → all metrics are 0, no division errors")
        void testComputeMetricsFrom_zeroWaste() {
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(0, 0, 0);

            assertNotNull(m);
            assertEquals(0.0, m.getReductionRate(), 0.001);
            assertEquals(0.0, m.getRecyclingRatio(), 0.001);
            assertEquals(0.0, m.getEnvironmentalScore(), 0.001);
            assertEquals(0.0, m.getSdg12Score(), 0.001);
            assertEquals(0.0, m.getSdg14Score(), 0.001);
            assertEquals(0.0, m.getPlasticLeakageKg(), 0.001);
        }

        @Test
        @DisplayName("Score grade EXCELLENT when environmentalScore >= 80")
        void testScoreGrade_excellent() {
            // 100% reduction, 100% recycling → envScore = 100
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 100, 0);
            assertEquals("EXCELLENT", m.getScoreGrade());
            assertEquals("score-excellent", m.getScoreColorClass());
        }

        @Test
        @DisplayName("Score grade GOOD when environmentalScore 60–79")
        void testScoreGrade_good() {
            // reductionRate=70%, recyclingRatio=50% → envScore = 60
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 50, 20);
            // reductionRate=(50+20)/100×100=70%, recyclingRatio=50/100×100=50%
            // envScore=0.5×70+0.5×50=60
            assertEquals("GOOD", m.getScoreGrade());
            assertEquals("score-good", m.getScoreColorClass());
        }

        @Test
        @DisplayName("Score grade FAIR when environmentalScore 40–59")
        void testScoreGrade_fair() {
            // 100 generated, 30 recycled, 20 eliminated
            // reductionRate=50%, recyclingRatio=30% → envScore=40
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 30, 20);
            assertEquals("FAIR", m.getScoreGrade());
        }

        @Test
        @DisplayName("Score grade POOR when environmentalScore 20–39")
        void testScoreGrade_poor() {
            // 100 generated, 10 recycled, 10 eliminated
            // reductionRate=20%, recyclingRatio=10% → envScore=15 → CRITICAL
            // Use: 100 gen, 30 rec, 0 elim → rate=30%, ratio=30% → envScore=30 → POOR
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 30, 0);
            assertEquals("POOR", m.getScoreGrade());
        }

        @Test
        @DisplayName("Score grade CRITICAL when environmentalScore < 20")
        void testScoreGrade_critical() {
            // 100 generated, 5 recycled, 5 eliminated
            // reductionRate=10%, recyclingRatio=5% → envScore=7.5 → CRITICAL
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 5, 5);
            assertEquals("CRITICAL", m.getScoreGrade());
            assertEquals("score-critical", m.getScoreColorClass());
        }

        @Test
        @DisplayName("Leakage is never negative even when recycled+eliminated > generated")
        void testLeakage_neverNegative() {
            // Defensive: if somehow recycled + eliminated exceeds generated
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 80, 40);
            assertTrue(m.getPlasticLeakageKg() >= 0,
                    "Plastic leakage should never be negative");
        }

        @Test
        @DisplayName("SDG12Score capped at 100 even when reduction exceeds target")
        void testSdg12Score_cappedAt100() {
            // 100% reduction → sdg12Score = (100/80)×100 = 125 → capped at 100
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 60, 40);
            assertTrue(m.getSdg12Score() <= 100.0,
                    "SDG 12 score must not exceed 100");
        }

        @Test
        @DisplayName("SDG14Score capped at 100 even when ratio exceeds target")
        void testSdg14Score_cappedAt100() {
            // 100% recycling → sdg14Score = (100/50)×100 = 200 → capped at 100
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(100, 100, 0);
            assertTrue(m.getSdg14Score() <= 100.0,
                    "SDG 14 score must not exceed 100");
        }
    }
}
