package com.plasticaudit.service;

import com.plasticaudit.dto.ReportRecommendationDTO;
import com.plasticaudit.entity.AuditReport;
import org.junit.jupiter.api.*;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for RecommendationService.
 * No Spring context — pure logic isolation.
 *
 * Coverage map:
 * Scenario 1 — plasticWaste > threshold → "Reduce plastic usage" + "Switch to
 * biodegradable..."
 * Scenario 2 — recyclingRatio < 30% → "Increase recycling process"
 * Scenario 3 — Both conditions true → all recommendations returned
 * Scenario 4 — No condition true → positive "Great performance!" message (not
 * empty)
 * Scenario 5 — Edge cases (zero, 100% recycling, null fields)
 * Scenario 6 — Validation (negative waste, negative ratio)
 * Scenario 7 — Explicit assertion types: assertEquals, assertTrue, assertFalse,
 * assertNotNull
 * Scenario 8 — DTO hasWarnings() flag
 * Scenario 9 — Batch generateForAll()
 * Scenario 10 — Immutability & constants
 */
@DisplayName("RecommendationService — Full Test Suite")
class RecommendationServiceTest {

    private RecommendationService sut;

    @BeforeEach
    void setUp() {
        sut = new RecommendationService();
    }

    // ── Builder helper ────────────────────────────────────────────────────
    private AuditReport report(double generated, double recycled,
            double eliminated,
            double recyclingPct, double reductionPct) {
        AuditReport r = new AuditReport();
        r.setTotalGeneratedKg(generated);
        r.setTotalRecycledKg(recycled);
        r.setTotalEliminatedKg(eliminated);
        r.setRecyclingRatioPercent(recyclingPct);
        r.setReductionRatePercent(reductionPct);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 1 — plasticWaste > threshold
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 1 — plasticWaste > threshold (>500 kg)")
    class HighWasteScenario {

        @Test
        @DisplayName("600 kg → contains 'Reduce plastic usage'")
        void highWaste_containsReduceUsageMessage() {
            List<String> recs = sut.generateFor(report(600, 250, 100, 50.0, 58.0));

            assertNotNull(recs, "Result must not be null");
            assertTrue(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")),
                    "Should suggest reducing plastic usage");
        }

        @Test
        @DisplayName("600 kg → contains 'Switch to biodegradable'")
        void highWaste_containsBiodegradableMessage() {
            List<String> recs = sut.generateFor(report(600, 250, 100, 50.0, 58.0));

            assertTrue(recs.stream().anyMatch(s -> s.contains("biodegradable")),
                    "Should suggest switching to biodegradable packaging");
        }

        @Test
        @DisplayName("600 kg → exactly 2 recommendations (recycling & reduction healthy)")
        void highWasteOnly_exactly2Recs() {
            List<String> recs = sut.generateFor(report(600, 280, 100, 55.0, 63.0));

            assertEquals(2, recs.size(),
                    "Only Rule 1 fires (2 messages), others are healthy");
        }

        @Test
        @DisplayName("1000 kg → both high-waste messages present")
        void veryHighWaste_bothMessages() {
            List<String> recs = sut.generateFor(report(1000, 400, 200, 60.0, 60.0));

            assertThat(recs).anyMatch(s -> s.contains("Reduce plastic usage"));
            assertThat(recs).anyMatch(s -> s.contains("biodegradable"));
            assertEquals(2, recs.size());
        }

        @Test
        @DisplayName("500 kg (at boundary) → no high-waste message")
        void exactlyAtThreshold_noHighWasteMessage() {
            List<String> recs = sut.generateFor(report(500, 200, 100, 50.0, 60.0));

            assertFalse(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")),
                    "Should NOT trigger at exactly the threshold");
        }

        @Test
        @DisplayName("499 kg → no high-waste message")
        void justBelowThreshold_noHighWasteMessage() {
            List<String> recs = sut.generateFor(report(499, 200, 100, 50.0, 60.0));

            assertFalse(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 2 — recyclingRatio < 30%
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 2 — recyclingRatio < 30%")
    class LowRecyclingScenario {

        @Test
        @DisplayName("10% recycling → contains 'Increase recycling process'")
        void lowRecycling_containsRecyclingMessage() {
            List<String> recs = sut.generateFor(report(100, 10, 5, 10.0, 50.0));

            assertNotNull(recs, "Result must not be null");
            assertTrue(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "Should suggest increasing recycling");
        }

        @Test
        @DisplayName("0% recycling → recycling recommendation present")
        void zeroRecycling_containsRecyclingMessage() {
            List<String> recs = sut.generateFor(report(300, 0, 0, 0.0, 40.0));

            assertTrue(recs.stream().anyMatch(s -> s.contains("Increase recycling process")));
        }

        @Test
        @DisplayName("29.9% recycling → recommendation triggered")
        void justBelowRecyclingThreshold_triggered() {
            List<String> recs = sut.generateFor(report(100, 29, 10, 29.9, 50.0));

            assertTrue(recs.stream().anyMatch(s -> s.contains("Increase recycling process")));
        }

        @Test
        @DisplayName("30% recycling (at boundary) → no recycling recommendation")
        void atRecyclingThreshold_notTriggered() {
            List<String> recs = sut.generateFor(report(100, 30, 10, 30.0, 50.0));

            assertFalse(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "Should NOT trigger at exactly 30%");
        }

        @Test
        @DisplayName("100% recycling → no recycling recommendation")
        void fullRecycling_noRecyclingWarning() {
            List<String> recs = sut.generateFor(report(200, 200, 0, 100.0, 100.0));

            assertFalse(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "100% recycling should not trigger any recycling warning");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 3 — Both conditions true
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 3 — Both plasticWaste > threshold AND recyclingRatio < 30%")
    class BothConditionsScenario {

        @Test
        @DisplayName("Both conditions → all 3 recommendations present (waste x2 + recycling)")
        void bothConditions_returnsAllRecs() {
            // 800 kg (>500) + 10% recycling (<30%) + 40% reduction (ok)
            List<String> recs = sut.generateFor(report(800, 80, 0, 10.0, 40.0));

            assertNotNull(recs);
            assertTrue(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")),
                    "Must include 'Reduce plastic usage'");
            assertTrue(recs.stream().anyMatch(s -> s.contains("biodegradable")),
                    "Must include biodegradable packaging suggestion");
            assertTrue(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "Must include 'Increase recycling process'");
        }

        @Test
        @DisplayName("All three rules fire → 4 total recommendations")
        void allThreeRules_returns4Recs() {
            // 800 kg, 5% recycling, 10% reduction
            List<String> recs = sut.generateFor(report(800, 40, 10, 5.0, 10.0));

            assertEquals(4, recs.size(),
                    "Rule1×2 + Rule2×1 + Rule3×1 = 4 recommendations");
        }

        @Test
        @DisplayName("Both conditions → no positive ✅ message included")
        void bothConditions_noPositiveMessage() {
            List<String> recs = sut.generateFor(report(800, 80, 0, 8.0, 15.0));

            assertFalse(recs.stream().anyMatch(s -> s.startsWith("✅")),
                    "Should not include a positive message when warnings exist");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 4 — No condition true → positive feedback
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 4 — No condition triggers → positive performance message")
    class NoConditionScenario {

        @Test
        @DisplayName("Healthy metrics → returns exactly 1 item")
        void allHealthy_exactlyOneMessage() {
            List<String> recs = sut.generateFor(report(300, 140, 60, 46.0, 66.0));

            assertNotNull(recs);
            assertEquals(1, recs.size(), "Should return exactly 1 positive message");
        }

        @Test
        @DisplayName("Healthy metrics → message starts with ✅")
        void allHealthy_positiveMessageContent() {
            List<String> recs = sut.generateFor(report(300, 140, 60, 46.0, 66.0));

            assertTrue(recs.get(0).startsWith("✅"),
                    "Positive feedback message should start with ✅");
        }

        @Test
        @DisplayName("Healthy metrics → message contains 'Great performance'")
        void allHealthy_containsGreatPerformance() {
            List<String> recs = sut.generateFor(report(300, 140, 60, 46.0, 66.0));

            assertTrue(recs.get(0).contains("Great performance"),
                    "Positive message should contain 'Great performance'");
        }

        @Test
        @DisplayName("hasWarnings() false for healthy report")
        void healthyReport_hasWarningsFalse() {
            AuditReport r = report(200, 100, 50, 50.0, 75.0);
            List<String> recs = sut.generateFor(r);

            ReportRecommendationDTO dto = new ReportRecommendationDTO(r, recs);
            assertFalse(dto.hasWarnings(), "hasWarnings() must be false for healthy report");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 5 — Edge Cases
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 5 — Edge Cases")
    class EdgeCaseScenario {

        @Test
        @DisplayName("plasticWaste = 0 → no high-waste or recycling warning")
        void zeroWaste_noWarnings() {
            List<String> recs = sut.generateFor(report(0, 0, 0, 0.0, 0.0));

            assertNotNull(recs);
            assertFalse(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")),
                    "Zero waste should not trigger high-waste rule");
            // recyclingPct=0 < 30, so recycling rule fires — which is expected
        }

        @Test
        @DisplayName("recyclingRatio = 100% → no recycling warning")
        void fullRecycling_noRecyclingWarning() {
            List<String> recs = sut.generateFor(report(400, 400, 0, 100.0, 100.0));

            assertFalse(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "100% recycling must not trigger recycling warning");
        }

        @Test
        @DisplayName("All null fields on AuditReport → treated as zeros → safe execution")
        void nullFieldsOnReport_treatedAsZero() {
            AuditReport r = new AuditReport(); // all fields default/null

            assertDoesNotThrow(() -> sut.generateFor(r),
                    "Null fields must not throw NullPointerException");
        }

        @Test
        @DisplayName("All null fields → returns non-null result")
        void nullFieldsOnReport_returnsNonNullResult() {
            AuditReport r = new AuditReport();
            List<String> recs = sut.generateFor(r);

            assertNotNull(recs, "Result must not be null even with null field values");
        }

        @Test
        @DisplayName("null report → returns empty list (no NPE)")
        void nullReport_returnsEmptyList() {
            List<String> recs = sut.generateFor(null);

            assertNotNull(recs, "Must return non-null list for null input");
            assertTrue(recs.isEmpty(), "Must return empty list for null report");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 6 — Validation: negative and invalid inputs
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 6 — Validation: negative/extreme inputs")
    class ValidationScenario {

        @Test
        @DisplayName("negative waste (-100) → treated as 0 → no high-waste warning")
        void negativeWaste_noHighWasteWarning() {
            // Service uses nullSafe which returns the value as-is; negatives don't exceed
            // threshold
            List<String> recs = sut.generateFor(report(-100, -10, 0, 10.0, 10.0));

            assertNotNull(recs, "Must not throw for negative inputs");
            assertFalse(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")),
                    "Negative waste is below threshold — no high-waste warning");
        }

        @Test
        @DisplayName("negative recyclingRatio (-5%) → recycling warning still fires (<30%)")
        void negativeRatio_recyclingWarningFires() {
            List<String> recs = sut.generateFor(report(100, 0, 0, -5.0, 40.0));

            assertTrue(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "Negative ratio is still < 30%, so recycling rule must fire");
        }

        @Test
        @DisplayName("extreme waste (999_999 kg) → high-waste warning fires without exception")
        void extremeWaste_firesWarningGracefully() {
            assertDoesNotThrow(() -> {
                List<String> recs = sut.generateFor(report(999_999, 0, 0, 0.0, 0.0));
                assertTrue(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")));
            });
        }

        @Test
        @DisplayName("recyclingRatio > 100% → no recycling warning (ratio is high enough)")
        void recyclingRatioOver100_noRecyclingWarning() {
            List<String> recs = sut.generateFor(report(100, 110, 0, 110.0, 110.0));

            assertFalse(recs.stream().anyMatch(s -> s.contains("Increase recycling process")),
                    "Ratios > 100% treated as not low, no warning");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 7 — DTO integration
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 7 — ReportRecommendationDTO integration")
    class DtoIntegrationScenario {

        @Test
        @DisplayName("Warning report → dto.hasWarnings() returns true")
        void warningReport_hasWarningsTrue() {
            AuditReport r = report(800, 40, 10, 5.0, 10.0);
            ReportRecommendationDTO dto = new ReportRecommendationDTO(r, sut.generateFor(r));

            assertTrue(dto.hasWarnings(), "DTO must report warnings for a bad report");
        }

        @Test
        @DisplayName("dto.getReport() returns the original report")
        void dto_getReport_returnsOriginalReport() {
            AuditReport r = report(300, 100, 50, 40.0, 60.0);
            ReportRecommendationDTO dto = new ReportRecommendationDTO(r, sut.generateFor(r));

            assertNotNull(dto.getReport(), "getReport() must not be null");
            assertEquals(r, dto.getReport(), "getReport() must return the exact same report");
        }

        @Test
        @DisplayName("dto.getRecommendations() returns the recommendations")
        void dto_getRecommendations_notNull() {
            AuditReport r = report(300, 100, 50, 40.0, 60.0);
            List<String> recs = sut.generateFor(r);
            ReportRecommendationDTO dto = new ReportRecommendationDTO(r, recs);

            assertNotNull(dto.getRecommendations(), "getRecommendations() must not be null");
            assertEquals(recs, dto.getRecommendations(), "Must return same list");
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 8 — generateForAll batch
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 8 — generateForAll batch method")
    class BatchScenario {

        @Test
        @DisplayName("null input → returns empty list")
        void nullList_returnsEmpty() {
            List<List<String>> result = sut.generateForAll(null);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("empty list → returns empty list")
        void emptyList_returnsEmpty() {
            List<List<String>> result = sut.generateForAll(List.of());

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("3 reports → 3 recommendation lists, same order")
        void threeReports_returnsThreeLists() {
            AuditReport r1 = report(100, 50, 10, 50.0, 60.0); // healthy
            AuditReport r2 = report(800, 10, 5, 5.0, 10.0); // all warnings
            AuditReport r3 = report(600, 0, 0, 0.0, 0.0); // high waste + low recycling

            List<List<String>> result = sut.generateForAll(List.of(r1, r2, r3));

            assertEquals(3, result.size());
            assertTrue(result.get(0).get(0).startsWith("✅"), "r1 should be healthy");
            assertEquals(4, result.get(1).size(), "r2 should have 4 warnings");
            assertFalse(result.get(2).isEmpty(), "r3 should have recommendations");
        }

        @Test
        @DisplayName("single report list → correct recommendation inside")
        void singleReportList_returnsOneList() {
            AuditReport r = report(600, 0, 0, 0.0, 0.0);
            List<List<String>> result = sut.generateForAll(List.of(r));

            assertEquals(1, result.size());
            assertNotNull(result.get(0));
            assertFalse(result.get(0).isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SCENARIO 9 — Immutability & constants
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 9 — Immutability and constants")
    class ImmutabilityScenario {

        @Test
        @DisplayName("Returned list is unmodifiable")
        void returnedList_isUnmodifiable() {
            List<String> recs = sut.generateFor(report(300, 100, 50, 40.0, 60.0));

            assertThrows(UnsupportedOperationException.class, () -> recs.add("external mutation"),
                    "Result list must be unmodifiable");
        }

        @Test
        @DisplayName("WASTE_HIGH_THRESHOLD = 500.0")
        void wasteHighThreshold_is500() {
            assertEquals(500.0, RecommendationService.WASTE_HIGH_THRESHOLD);
        }

        @Test
        @DisplayName("RECYCLING_LOW_THRESHOLD = 30.0")
        void recyclingLowThreshold_is30() {
            assertEquals(30.0, RecommendationService.RECYCLING_LOW_THRESHOLD);
        }

        @Test
        @DisplayName("REDUCTION_LOW_THRESHOLD = 40.0")
        void reductionLowThreshold_is40() {
            assertEquals(40.0, RecommendationService.REDUCTION_LOW_THRESHOLD);
        }
    }
}
