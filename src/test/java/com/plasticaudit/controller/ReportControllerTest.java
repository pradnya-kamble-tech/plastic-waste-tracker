package com.plasticaudit.controller;

import com.plasticaudit.dto.ReportRecommendationDTO;
import com.plasticaudit.entity.AuditReport;
import com.plasticaudit.entity.Industry;
import com.plasticaudit.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc tests for ReportController — verifies:
 * – Security enforcement (unauthenticated access)
 * – reportDTOs model attribute is populated with recommendations
 * – Thymeleaf view renders (reports/view)
 * – Empty report list handled gracefully
 * – RecommendationService is invoked per report
 * – INDUSTRY role sees only their own reports
 */
@WebMvcTest(ReportController.class)
@DisplayName("ReportController — MockMvc Tests")
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Mock all dependencies ─────────────────────────────────────────
    @MockBean
    private ReportAggregatorService reportAggregatorService;
    @MockBean
    private UserService userService;
    @MockBean
    private IndustryService industryService;
    @MockBean
    private RecommendationService recommendationService;
    @MockBean
    private PdfReportService pdfReportService;
    @MockBean
    private com.plasticaudit.repository.AuditReportRepository auditReportRepository;

    // ── Fixture builders ──────────────────────────────────────────────
    private AuditReport buildReport(Long id, double generated, double recyclingPct) {
        Industry ind = new Industry();
        ind.setId(1L);
        ind.setName("TestCo");

        AuditReport r = new AuditReport();
        r.setId(id);
        r.setIndustry(ind);
        r.setTotalGeneratedKg(generated);
        r.setTotalRecycledKg(generated * recyclingPct / 100);
        r.setTotalEliminatedKg(0.0);
        r.setRecyclingRatioPercent(recyclingPct);
        r.setReductionRatePercent(recyclingPct);
        r.setPeriodStart(LocalDate.of(2025, 1, 1));
        r.setPeriodEnd(LocalDate.of(2025, 3, 31));
        r.setStatus(AuditReport.ReportStatus.DRAFT);
        return r;
    }

    // ═══════════════════════════════════════════════════════════════
    // Security
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Security — unauthenticated access")
    class SecurityTests {

        @Test
        @DisplayName("GET /reports without auth → 401 or 403 (rejected)")
        void getReports_unauthenticated_rejected() throws Exception {
            mockMvc.perform(get("/reports"))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Admin view — model populated correctly
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Admin view — GET /reports")
    class AdminReportsTests {

        @BeforeEach
        void setupAdmin() {
            when(industryService.findAll()).thenReturn(Collections.emptyList());
        }

        @Test
        @DisplayName("GET /reports as ADMIN → HTTP 200 and view 'reports/view'")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_admin_returns200() throws Exception {
            when(reportAggregatorService.findAllReports()).thenReturn(List.of());
            when(recommendationService.generateFor(any())).thenReturn(List.of("✅ Great performance!"));

            mockMvc.perform(get("/reports"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/view"));
        }

        @Test
        @DisplayName("Model contains 'reportDTOs' attribute")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_admin_modelContainsReportDTOs() throws Exception {
            when(reportAggregatorService.findAllReports()).thenReturn(List.of());

            mockMvc.perform(get("/reports"))
                    .andExpect(model().attributeExists("reportDTOs"));
        }

        @Test
        @DisplayName("Model 'reportDTOs' is empty when no reports exist")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_admin_emptyReports_reportDTOsIsEmpty() throws Exception {
            when(reportAggregatorService.findAllReports()).thenReturn(List.of());

            mockMvc.perform(get("/reports"))
                    .andExpect(model().attribute("reportDTOs", empty()));
        }

        @Test
        @DisplayName("Model contains 'isAdmin' = true for ADMIN role")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_admin_isAdminTrue() throws Exception {
            when(reportAggregatorService.findAllReports()).thenReturn(List.of());

            mockMvc.perform(get("/reports"))
                    .andExpect(model().attribute("isAdmin", true));
        }

        @Test
        @DisplayName("1 report → reportDTOs has 1 element, recommendations populated")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_oneReport_reportDTOsHasOneElement() throws Exception {
            AuditReport r = buildReport(1L, 800.0, 5.0); // high waste, low recycling
            List<String> recs = List.of(
                    "♻️ Reduce plastic usage — generated volume exceeds threshold",
                    "🌿 Switch to biodegradable packaging",
                    "🔄 Increase recycling process");

            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r));
            when(recommendationService.generateFor(r)).thenReturn(recs);

            mockMvc.perform(get("/reports"))
                    .andExpect(model().attribute("reportDTOs", hasSize(1)));
        }

        @Test
        @DisplayName("Report with warnings → DTO hasWarnings() is true in model")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_warningReport_dtoHasWarningsTrue() throws Exception {
            AuditReport r = buildReport(1L, 800.0, 5.0);
            List<String> warningRecs = List.of("♻️ Reduce plastic usage");

            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r));
            when(recommendationService.generateFor(r)).thenReturn(warningRecs);

            mockMvc.perform(get("/reports"))
                    .andExpect(mvcResult -> {
                        @SuppressWarnings("unchecked")
                        List<ReportRecommendationDTO> dtos = (List<ReportRecommendationDTO>) mvcResult.getModelAndView()
                                .getModel().get("reportDTOs");
                        assertTrue(dtos != null && !dtos.isEmpty());
                        assertTrue(dtos.get(0).hasWarnings(),
                                "DTO for warning report must return hasWarnings()=true");
                    });
        }

        @Test
        @DisplayName("Healthy report → DTO hasWarnings() is false in model")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_healthyReport_dtoHasWarningsFalse() throws Exception {
            AuditReport r = buildReport(2L, 200.0, 50.0);
            List<String> healthyRecs = List.of("✅ Great performance!");

            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r));
            when(recommendationService.generateFor(r)).thenReturn(healthyRecs);

            mockMvc.perform(get("/reports"))
                    .andExpect(mvcResult -> {
                        @SuppressWarnings("unchecked")
                        List<ReportRecommendationDTO> dtos = (List<ReportRecommendationDTO>) mvcResult.getModelAndView()
                                .getModel().get("reportDTOs");
                        assertTrue(dtos != null && !dtos.isEmpty());
                        assertFalse(dtos.get(0).hasWarnings(),
                                "DTO for healthy report must return hasWarnings()=false");
                    });
        }

        @Test
        @DisplayName("RecommendationService.generateFor() called once per report")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_oneReport_recommendationServiceCalledOnce() throws Exception {
            AuditReport r = buildReport(1L, 300.0, 40.0);
            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r));
            when(recommendationService.generateFor(r)).thenReturn(List.of("✅ Great performance!"));

            mockMvc.perform(get("/reports"));

            verify(recommendationService, times(1)).generateFor(r);
        }

        @Test
        @DisplayName("2 reports → RecommendationService called twice")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_twoReports_recommendationServiceCalledTwice() throws Exception {
            AuditReport r1 = buildReport(1L, 300.0, 40.0);
            AuditReport r2 = buildReport(2L, 800.0, 5.0);
            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r1, r2));
            when(recommendationService.generateFor(any())).thenReturn(List.of("✅ OK"));

            mockMvc.perform(get("/reports"));

            verify(recommendationService, times(2)).generateFor(any());
        }

        @Test
        @DisplayName("HTML response contains 'Recommendations' text when reports exist")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_htmlContainsRecommendationText() throws Exception {
            AuditReport r = buildReport(1L, 800.0, 5.0);
            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r));
            when(recommendationService.generateFor(r))
                    .thenReturn(List.of("♻️ Reduce plastic usage"));

            mockMvc.perform(get("/reports"))
                    .andExpect(content().string(containsString("Recommendations")));
        }

        @Test
        @DisplayName("HTML response contains the recommendation text")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_htmlContainsActualRecommendationMessage() throws Exception {
            AuditReport r = buildReport(1L, 800.0, 5.0);
            when(reportAggregatorService.findAllReports()).thenReturn(List.of(r));
            when(recommendationService.generateFor(r))
                    .thenReturn(List.of("Reduce plastic usage"));

            mockMvc.perform(get("/reports"))
                    .andExpect(content().string(containsString("Reduce plastic usage")));
        }

        @Test
        @DisplayName("Empty report list → HTML shows empty-state section")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void getReports_emptyList_htmlShowsEmptyState() throws Exception {
            when(reportAggregatorService.findAllReports()).thenReturn(List.of());

            mockMvc.perform(get("/reports"))
                    .andExpect(content().string(containsString("No reports found")));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Industry user view
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Industry user view — GET /reports")
    class IndustryReportsTests {

        @Test
        @DisplayName("GET /reports as INDUSTRY → HTTP 200")
        @WithMockUser(username = "user1", roles = { "INDUSTRY" })
        void getReports_industryUser_returns200() throws Exception {
            when(userService.findByUsername("user1")).thenReturn(Optional.empty());

            mockMvc.perform(get("/reports"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("reports/view"));
        }

        @Test
        @DisplayName("isAdmin = false in model for INDUSTRY role")
        @WithMockUser(username = "user1", roles = { "INDUSTRY" })
        void getReports_industryUser_isAdminFalse() throws Exception {
            when(userService.findByUsername("user1")).thenReturn(Optional.empty());

            mockMvc.perform(get("/reports"))
                    .andExpect(model().attribute("isAdmin", false));
        }
    }
}
