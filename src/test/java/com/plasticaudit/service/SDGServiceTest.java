package com.plasticaudit.service;

import com.plasticaudit.repository.WasteEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Service-layer unit test for SDGService.computeGlobalMetrics().
 * Mocks WasteEntryRepository to isolate the service from the database.
 *
 * Covers:
 * - Normal data flow: repository returns aggregated metrics
 * - Empty database: no entries → all zeros
 * - Null row values: graceful null handling
 * - computeMetricsFrom() with explicit values
 */
@ExtendWith(MockitoExtension.class)
class SDGServiceTest {

    @Mock
    private WasteEntryRepository wasteEntryRepository;

    @InjectMocks
    private SDGService sdgService;

    // ═══════════════════════════════════════════════════════════════════
    // computeGlobalMetrics() — with mocked repository
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("computeGlobalMetrics() with mocked WasteEntryRepository")
    class GlobalMetricsTests {

        @Test
        @DisplayName("Repository returns real data → metrics computed correctly")
        void testComputeGlobalMetrics_withRealData() {
            // Arrange: simulate DB returning [generated=1000, recycled=600, eliminated=200]
            Object[] row = { 1000.0, 600.0, 200.0 };
            when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(List.<Object[]>of(row));

            // Act
            SDGService.SDGMetrics metrics = sdgService.computeGlobalMetrics();

            // Assert — basic non-null
            assertNotNull(metrics, "Metrics must not be null");

            // Totals
            assertEquals(1000.0, metrics.getTotalGeneratedKg(), 0.001);
            assertEquals(600.0, metrics.getTotalRecycledKg(), 0.001);
            assertEquals(200.0, metrics.getTotalEliminatedKg(), 0.001);

            // reductionRate = (600+200)/1000 × 100 = 80%
            assertEquals(80.0, metrics.getReductionRate(), 0.001,
                    "Reduction rate = (recycled+eliminated)/generated × 100");

            // recyclingRatio = 600/1000 × 100 = 60%
            assertEquals(60.0, metrics.getRecyclingRatio(), 0.001,
                    "Recycling ratio = recycled/generated × 100");

            // environmentalScore = 0.5×80 + 0.5×60 = 70
            assertEquals(70.0, metrics.getEnvironmentalScore(), 0.001,
                    "Environmental score = 50% × reductionRate + 50% × recyclingRatio");

            // sdg12Score = reductionRate/80 × 100 = 100% (at target)
            assertEquals(100.0, metrics.getSdg12Score(), 0.001);

            // sdg14Score = recyclingRatio/50 × 100 = 120% → capped at 100
            assertEquals(100.0, metrics.getSdg14Score(), 0.001);

            // leakageKg = 1000 - 600 - 200 = 200
            assertEquals(200.0, metrics.getPlasticLeakageKg(), 0.001);
            // leakageRate = 200/1000 × 100 = 20%
            assertEquals(20.0, metrics.getLeakageRate(), 0.001);

            // Grade: score=70 → GOOD
            assertEquals("GOOD", metrics.getScoreGrade());

            // Verify the repository was called exactly once
            verify(wasteEntryRepository, times(1)).getGlobalSdgMetrics();
        }

        @Test
        @DisplayName("Repository returns empty list (no waste entries) → all zeros")
        void testComputeGlobalMetrics_emptyDatabase() {
            when(wasteEntryRepository.getGlobalSdgMetrics())
                    .thenReturn(Collections.emptyList());

            SDGService.SDGMetrics metrics = sdgService.computeGlobalMetrics();

            assertNotNull(metrics);
            assertEquals(0.0, metrics.getTotalGeneratedKg(), 0.001);
            assertEquals(0.0, metrics.getTotalRecycledKg(), 0.001);
            assertEquals(0.0, metrics.getTotalEliminatedKg(), 0.001);
            assertEquals(0.0, metrics.getReductionRate(), 0.001);
            assertEquals(0.0, metrics.getRecyclingRatio(), 0.001);
            assertEquals(0.0, metrics.getEnvironmentalScore(), 0.001);
            assertEquals(0.0, metrics.getSdg12Score(), 0.001);
            assertEquals(0.0, metrics.getSdg14Score(), 0.001);
            assertEquals(0.0, metrics.getPlasticLeakageKg(), 0.001);
            assertEquals("CRITICAL", metrics.getScoreGrade(),
                    "Empty data should yield CRITICAL grade");
        }

        @Test
        @DisplayName("Repository returns null values in row → treated as 0")
        void testComputeGlobalMetrics_nullRowValues() {
            Object[] row = { null, null, null };
            when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(List.<Object[]>of(row));

            SDGService.SDGMetrics metrics = sdgService.computeGlobalMetrics();

            assertNotNull(metrics);
            assertEquals(0.0, metrics.getTotalGeneratedKg(), 0.001);
            assertEquals(0.0, metrics.getReductionRate(), 0.001);
            assertEquals(0.0, metrics.getEnvironmentalScore(), 0.001);
        }

        @Test
        @DisplayName("Repository returns null list → treated as empty, all zeros")
        void testComputeGlobalMetrics_nullList() {
            when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(null);

            SDGService.SDGMetrics metrics = sdgService.computeGlobalMetrics();

            assertNotNull(metrics);
            assertEquals(0.0, metrics.getReductionRate(), 0.001);
        }

        @Test
        @DisplayName("High waste scenario → environmental scores are well below 100")
        void testComputeGlobalMetrics_highUnrecycledWaste() {
            // 10000 generated, only 200 recycled, 50 eliminated
            Object[] row = { 10000.0, 200.0, 50.0 };
            when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(List.<Object[]>of(row));

            SDGService.SDGMetrics metrics = sdgService.computeGlobalMetrics();

            // reductionRate = (200+50)/10000 × 100 = 2.5%
            assertEquals(2.5, metrics.getReductionRate(), 0.001);
            // environmentalScore should be very low
            assertTrue(metrics.getEnvironmentalScore() < 20.0,
                    "High unrecycled waste should yield a very low env score");
            // Grade must be CRITICAL
            assertEquals("CRITICAL", metrics.getScoreGrade());
            // Leakage should be large
            assertEquals(9750.0, metrics.getPlasticLeakageKg(), 0.001);
        }

        @Test
        @DisplayName("Perfect recycling scenario → maximum SDG scores")
        void testComputeGlobalMetrics_perfectRecycling() {
            Object[] row = { 1000.0, 1000.0, 0.0 };
            when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(List.<Object[]>of(row));

            SDGService.SDGMetrics metrics = sdgService.computeGlobalMetrics();

            assertEquals(100.0, metrics.getReductionRate(), 0.001);
            assertEquals(100.0, metrics.getRecyclingRatio(), 0.001);
            assertEquals(100.0, metrics.getEnvironmentalScore(), 0.001);
            assertEquals(100.0, metrics.getSdg12Score(), 0.001);
            assertEquals(100.0, metrics.getSdg14Score(), 0.001);
            assertEquals(0.0, metrics.getPlasticLeakageKg(), 0.001);
            assertEquals("EXCELLENT", metrics.getScoreGrade());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // computeMetricsFrom() — no repository involved
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("computeMetricsFrom() — direct value computation")
    class MetricsFromTests {

        @Test
        @DisplayName("Typical industry data → metrics populated correctly")
        void testComputeMetricsFrom_typical() {
            SDGService.SDGMetrics m = sdgService.computeMetricsFrom(400, 200, 100);

            assertNotNull(m);
            assertEquals(400.0, m.getTotalGeneratedKg(), 0.001);
            assertEquals(200.0, m.getTotalRecycledKg(), 0.001);
            assertEquals(100.0, m.getTotalEliminatedKg(), 0.001);

            // reductionRate = (200+100)/400 × 100 = 75%
            assertEquals(75.0, m.getReductionRate(), 0.001);
            // recyclingRatio = 200/400 × 100 = 50%
            assertEquals(50.0, m.getRecyclingRatio(), 0.001);
            // envScore = 0.5×75 + 0.5×50 = 62.5
            assertEquals(62.5, m.getEnvironmentalScore(), 0.001);
            // leakage = 400 - 200 - 100 = 100
            assertEquals(100.0, m.getPlasticLeakageKg(), 0.001);

            // No repository call expected here
            verifyNoInteractions(wasteEntryRepository);
        }

        @Test
        @DisplayName("computeMetricsFrom with all zeros → no NPE")
        void testComputeMetricsFrom_allZeros() {
            assertDoesNotThrow(() -> sdgService.computeMetricsFrom(0, 0, 0),
                    "Zero inputs should not throw any exception");
        }
    }
}
