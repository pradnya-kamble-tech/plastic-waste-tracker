package com.plasticaudit.service;

import com.plasticaudit.entity.AuditReport;
import com.plasticaudit.entity.Industry;
import com.plasticaudit.entity.User;
import com.plasticaudit.repository.AuditReportRepository;
import com.plasticaudit.repository.IndustryRepository;
import com.plasticaudit.repository.WasteEntryRepository;
import com.plasticaudit.socket.AlertServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Mockito-based service integration tests for ReportAggregatorService.
 *
 * Verifies:
 * – Report building logic uses correct repo queries
 * – Recommendations integrate correctly with built reports
 * – Async aggregation completes and persists reports
 * – Edge cases: empty industry list, null waste values
 * – AlertServer is triggered exactly once per aggregation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportAggregatorService — Mockito Tests")
class ReportAggregatorServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────
    @Mock
    private IndustryRepository industryRepository;
    @Mock
    private WasteEntryRepository wasteEntryRepository;
    @Mock
    private AuditReportRepository auditReportRepository;
    @Mock
    private AlertServer alertServer;

    // ── Subject under test ────────────────────────────────────────────
    @InjectMocks
    private ReportAggregatorService sut;

    // ── RecommendationService is real (pure logic, no deps) ───────────
    private final RecommendationService recommendationService = new RecommendationService();

    // ── Common fixtures ───────────────────────────────────────────────
    private static Industry industry1, industry2;
    private static User adminUser;
    private static LocalDate start, end;

    @BeforeAll
    static void globalSetup() {
        industry1 = new Industry();
        industry1.setId(1L);
        industry1.setName("Green Plastics Ltd");

        industry2 = new Industry();
        industry2.setId(2L);
        industry2.setName("EcoPack Inc");

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");

        start = LocalDate.of(2025, 1, 1);
        end = LocalDate.of(2025, 3, 31);
    }

    // ═══════════════════════════════════════════════════════════════
    // findAllReports — query delegation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findAllReports — delegation to repository")
    class FindAllReportsTests {

        @Test
        @DisplayName("delegates to auditReportRepository.findAll()")
        void findAllReports_delegatesToRepository() {
            AuditReport r1 = new AuditReport();
            AuditReport r2 = new AuditReport();
            when(auditReportRepository.findAll()).thenReturn(List.of(r1, r2));

            List<AuditReport> result = sut.findAllReports();

            assertNotNull(result);
            assertEquals(2, result.size());
            verify(auditReportRepository, times(1)).findAll();
        }

        @Test
        @DisplayName("empty repository → returns empty list, not null")
        void findAllReports_emptyRepo_returnsEmptyList() {
            when(auditReportRepository.findAll()).thenReturn(List.of());

            List<AuditReport> result = sut.findAllReports();

            assertNotNull(result, "Result must never be null");
            assertTrue(result.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // findReportsByIndustry — delegation
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("findReportsByIndustry — delegation to repository")
    class FindByIndustryTests {

        @Test
        @DisplayName("returns reports for given industry ID")
        void findReportsByIndustry_returnsCorrectReports() {
            AuditReport r = new AuditReport();
            r.setIndustry(industry1);
            when(auditReportRepository.findReportsByIndustry(1L)).thenReturn(List.of(r));

            List<AuditReport> result = sut.findReportsByIndustry(1L);

            assertNotNull(result);
            assertEquals(1, result.size());
            assertEquals(industry1, result.get(0).getIndustry());
            verify(auditReportRepository).findReportsByIndustry(1L);
        }

        @Test
        @DisplayName("unknown industry ID → returns empty list")
        void findReportsByIndustry_unknownId_returnsEmpty() {
            when(auditReportRepository.findReportsByIndustry(999L)).thenReturn(List.of());

            List<AuditReport> result = sut.findReportsByIndustry(999L);

            assertTrue(result.isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // updateReportStatus — persistence
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("updateReportStatus — status change and save")
    class UpdateStatusTests {

        @Test
        @DisplayName("DRAFT → APPROVED: status is updated and saved")
        void updateStatus_foundReport_updatesAndSaves() {
            AuditReport r = new AuditReport();
            r.setStatus(AuditReport.ReportStatus.DRAFT);
            when(auditReportRepository.findById(1L))
                    .thenReturn(java.util.Optional.of(r));

            sut.updateReportStatus(1L, AuditReport.ReportStatus.APPROVED);

            assertEquals(AuditReport.ReportStatus.APPROVED, r.getStatus());
            verify(auditReportRepository).save(r);
        }

        @Test
        @DisplayName("unknown report ID → no save called")
        void updateStatus_notFoundReport_savesNothing() {
            when(auditReportRepository.findById(999L))
                    .thenReturn(java.util.Optional.empty());

            sut.updateReportStatus(999L, AuditReport.ReportStatus.APPROVED);

            verify(auditReportRepository, never()).save(any());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Async aggregation — RecommendationService integration
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("aggregateReportsForAllIndustries — integration with RecommendationService")
    class AsyncAggregationTests {

        @Test
        @DisplayName("2 industries → 2 reports built; recommendations generated per report")
        void twoIndustries_twoReportsBuilt_recommendationsGenerated()
                throws ExecutionException, InterruptedException, TimeoutException {

            // Arrange: 2 industries
            when(industryRepository.findAll()).thenReturn(List.of(industry1, industry2));

            // Industry 1 — healthy volumes
            when(wasteEntryRepository.sumGeneratedByIndustryAndPeriod(eq(1L), any(), any()))
                    .thenReturn(300.0);
            when(wasteEntryRepository.sumRecycledByIndustryAndPeriod(eq(1L), any(), any()))
                    .thenReturn(150.0);
            when(wasteEntryRepository.sumEliminatedByIndustryAndPeriod(eq(1L), any(), any()))
                    .thenReturn(50.0);

            // Industry 2 — high waste, low recycling
            when(wasteEntryRepository.sumGeneratedByIndustryAndPeriod(eq(2L), any(), any()))
                    .thenReturn(800.0);
            when(wasteEntryRepository.sumRecycledByIndustryAndPeriod(eq(2L), any(), any()))
                    .thenReturn(40.0);
            when(wasteEntryRepository.sumEliminatedByIndustryAndPeriod(eq(2L), any(), any()))
                    .thenReturn(10.0);

            when(auditReportRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(alertServer).broadcastAlert(anyString());

            // Act
            CompletableFuture<List<AuditReport>> future = sut.aggregateReportsForAllIndustries(start, end, adminUser);
            List<AuditReport> reports = future.get(10, TimeUnit.SECONDS);

            // Assert reports
            assertNotNull(reports);
            assertEquals(2, reports.size());

            // Assert recommendations for each built report
            for (AuditReport r : reports) {
                List<String> recs = recommendationService.generateFor(r);
                assertNotNull(recs, "Recommendations must not be null for any report");
                assertFalse(recs.isEmpty(), "Recommendations must not be empty");
            }
        }

        @Test
        @DisplayName("Industry 2 (high waste) → recommendation contains 'Reduce plastic usage'")
        void highWasteIndustry_recommendationContainsReduceUsage()
                throws ExecutionException, InterruptedException, TimeoutException {

            when(industryRepository.findAll()).thenReturn(List.of(industry2));
            when(wasteEntryRepository.sumGeneratedByIndustryAndPeriod(eq(2L), any(), any()))
                    .thenReturn(800.0);
            when(wasteEntryRepository.sumRecycledByIndustryAndPeriod(eq(2L), any(), any()))
                    .thenReturn(40.0);
            when(wasteEntryRepository.sumEliminatedByIndustryAndPeriod(eq(2L), any(), any()))
                    .thenReturn(10.0);
            when(auditReportRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(alertServer).broadcastAlert(anyString());

            List<AuditReport> reports = sut
                    .aggregateReportsForAllIndustries(start, end, adminUser)
                    .get(10, TimeUnit.SECONDS);

            assertEquals(1, reports.size());
            List<String> recs = recommendationService.generateFor(reports.get(0));

            assertTrue(recs.stream().anyMatch(s -> s.contains("Reduce plastic usage")),
                    "High-waste industry must trigger 'Reduce plastic usage' recommendation");
        }

        @Test
        @DisplayName("Industry 1 (healthy) → recommendations show positive ✅ message")
        void healthyIndustry_recommendationIsPositive()
                throws ExecutionException, InterruptedException, TimeoutException {

            when(industryRepository.findAll()).thenReturn(List.of(industry1));
            when(wasteEntryRepository.sumGeneratedByIndustryAndPeriod(eq(1L), any(), any()))
                    .thenReturn(200.0);
            when(wasteEntryRepository.sumRecycledByIndustryAndPeriod(eq(1L), any(), any()))
                    .thenReturn(100.0);
            when(wasteEntryRepository.sumEliminatedByIndustryAndPeriod(eq(1L), any(), any()))
                    .thenReturn(60.0);
            when(auditReportRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(alertServer).broadcastAlert(anyString());

            List<AuditReport> reports = sut
                    .aggregateReportsForAllIndustries(start, end, adminUser)
                    .get(10, TimeUnit.SECONDS);

            assertEquals(1, reports.size());
            List<String> recs = recommendationService.generateFor(reports.get(0));

            assertTrue(recs.get(0).startsWith("✅"),
                    "Healthy industry should receive positive feedback");
        }

        @Test
        @DisplayName("empty industry list → 0 reports, alertServer still called once")
        void noIndustries_noReports_alertStillFires()
                throws ExecutionException, InterruptedException, TimeoutException {

            when(industryRepository.findAll()).thenReturn(List.of());
            when(auditReportRepository.saveAll(anyList())).thenReturn(List.of());
            doNothing().when(alertServer).broadcastAlert(anyString());

            List<AuditReport> reports = sut
                    .aggregateReportsForAllIndustries(start, end, adminUser)
                    .get(10, TimeUnit.SECONDS);

            assertNotNull(reports);
            assertTrue(reports.isEmpty());
            verify(alertServer, times(1)).broadcastAlert(anyString());
        }

        @Test
        @DisplayName("null waste values from repository → treated as 0.0 (no NPE)")
        void nullWasteValues_treatedAsZero()
                throws ExecutionException, InterruptedException, TimeoutException {

            when(industryRepository.findAll()).thenReturn(List.of(industry1));
            // All repo queries return null
            when(wasteEntryRepository.sumGeneratedByIndustryAndPeriod(any(), any(), any()))
                    .thenReturn(null);
            when(wasteEntryRepository.sumRecycledByIndustryAndPeriod(any(), any(), any()))
                    .thenReturn(null);
            when(wasteEntryRepository.sumEliminatedByIndustryAndPeriod(any(), any(), any()))
                    .thenReturn(null);
            when(auditReportRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(alertServer).broadcastAlert(anyString());

            List<AuditReport> reports = sut
                    .aggregateReportsForAllIndustries(start, end, adminUser)
                    .get(10, TimeUnit.SECONDS);

            assertEquals(1, reports.size());
            AuditReport r = reports.get(0);
            assertEquals(0.0, r.getTotalGeneratedKg(), "Null→0 for generated");
            assertEquals(0.0, r.getTotalRecycledKg(), "Null→0 for recycled");
            assertEquals(0.0, r.getTotalEliminatedKg(), "Null→0 for eliminated");
        }

        @Test
        @DisplayName("saveAll() is called exactly once after aggregation")
        void aggregation_callsSaveAllOnce()
                throws ExecutionException, InterruptedException, TimeoutException {

            when(industryRepository.findAll()).thenReturn(List.of(industry1));
            when(wasteEntryRepository.sumGeneratedByIndustryAndPeriod(any(), any(), any()))
                    .thenReturn(300.0);
            when(wasteEntryRepository.sumRecycledByIndustryAndPeriod(any(), any(), any()))
                    .thenReturn(150.0);
            when(wasteEntryRepository.sumEliminatedByIndustryAndPeriod(any(), any(), any()))
                    .thenReturn(50.0);
            when(auditReportRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            doNothing().when(alertServer).broadcastAlert(anyString());

            sut.aggregateReportsForAllIndustries(start, end, adminUser).get(10, TimeUnit.SECONDS);

            verify(auditReportRepository, times(1)).saveAll(anyList());
        }
    }
}
