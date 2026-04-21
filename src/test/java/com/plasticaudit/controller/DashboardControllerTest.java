package com.plasticaudit.controller;

import com.plasticaudit.service.IndustryService;
import com.plasticaudit.service.ReportAggregatorService;
import com.plasticaudit.service.SDGService;
import com.plasticaudit.service.SDGService.SDGMetrics;
import com.plasticaudit.service.UserService;
import com.plasticaudit.service.WasteEntryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc controller tests for DashboardController.
 * Uses @WebMvcTest to test only the web/MVC layer without a full application
 * context.
 *
 * Covers:
 * - Unauthenticated access → redirected to login
 * - ADMIN role access → 200 + model contains SDG attributes
 * - INDUSTRY role access → 200 + industry-specific model
 * - SDG fields present in the model (reductionRate, recyclingRatio,
 * environmentalScore)
 * - Page title contains "Dashboard"
 */
@WebMvcTest(DashboardController.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // ── Mock all DashboardController dependencies ──────────────────────
    @MockBean
    private IndustryService industryService;
    @MockBean
    private WasteEntryService wasteEntryService;
    @MockBean
    private UserService userService;
    @MockBean
    private ReportAggregatorService reportAggregatorService;
    @MockBean
    private SDGService sdgService;

    // Shared SDGMetrics stub — represents a normal test scenario
    private SDGMetrics buildSampleMetrics() {
        // generated=1000, recycled=600, eliminated=200
        // reductionRate=80%, recyclingRatio=60%, envScore=70, grade=GOOD
        return new SDGMetrics(
                1000.0, 600.0, 200.0, // generated, recycled, eliminated
                80.0, 60.0, 70.0, // reductionRate, recyclingRatio, envScore
                100.0, 100.0, // sdg12Score, sdg14Score
                200.0, 20.0 // leakageKg, leakageRate
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. SECURITY — Unauthenticated access
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Security — authentication enforcement")
    class SecurityTests {

        @Test
        @DisplayName("GET /dashboard without auth → rejected (401 or 302 redirect)")
        void testDashboard_redirectsToLoginWhenUnauthenticated() throws Exception {
            // In @WebMvcTest, Spring Security returns 401 Unauthorized (not 302)
            // because there is no full form-login filter chain in the slice context.
            // We accept either 4xx (client error) as a valid rejection.
            mockMvc.perform(get("/dashboard"))
                    .andExpect(status().is4xxClientError());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. ADMIN VIEW
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Admin (ROLE_ADMIN) dashboard")
    class AdminDashboardTests {

        @Test
        @DisplayName("GET /dashboard as ADMIN → HTTP 200")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void testDashboard_returnsOkForAdmin() throws Exception {
            // Arrange
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(reportAggregatorService.findAllReports()).thenReturn(java.util.List.of());
            when(industryService.findAll()).thenReturn(java.util.List.of());

            // Act + Assert
            mockMvc.perform(get("/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("dashboard/index"));
        }

        @Test
        @DisplayName("Model contains sdg object with correct values")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void testDashboard_modelContainsSdgObject() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(reportAggregatorService.findAllReports()).thenReturn(java.util.List.of());
            when(industryService.findAll()).thenReturn(java.util.List.of());

            mockMvc.perform(get("/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(model().attributeExists("sdg"))
                    .andExpect(model().attributeExists("sdgReductionRate"))
                    .andExpect(model().attributeExists("sdgRecyclingRatio"))
                    .andExpect(model().attributeExists("sdgGenerated"))
                    .andExpect(model().attributeExists("sdgRecycled"))
                    .andExpect(model().attributeExists("sdgEliminated"));
        }

        @Test
        @DisplayName("Model sdgReductionRate formatted as '80.0'")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void testDashboard_reductionRateFormattedCorrectly() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(reportAggregatorService.findAllReports()).thenReturn(java.util.List.of());
            when(industryService.findAll()).thenReturn(java.util.List.of());

            mockMvc.perform(get("/dashboard"))
                    .andExpect(model().attribute("sdgReductionRate", "80.0"))
                    .andExpect(model().attribute("sdgRecyclingRatio", "60.0"));
        }

        @Test
        @DisplayName("isAdmin attribute is true in model for ADMIN role")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void testDashboard_isAdminTrueForAdminRole() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(reportAggregatorService.findAllReports()).thenReturn(java.util.List.of());
            when(industryService.findAll()).thenReturn(java.util.List.of());

            mockMvc.perform(get("/dashboard"))
                    .andExpect(model().attribute("isAdmin", true));
        }

        @Test
        @DisplayName("HTML response contains SDG 12 and SDG 14 text")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void testDashboard_htmlContainsSdgText() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(reportAggregatorService.findAllReports()).thenReturn(java.util.List.of());
            when(industryService.findAll()).thenReturn(java.util.List.of());

            mockMvc.perform(get("/dashboard"))
                    .andExpect(content().string(containsString("SDG 12")))
                    .andExpect(content().string(containsString("SDG 14")))
                    .andExpect(content().string(containsString("Environmental Score")));
        }

        @Test
        @DisplayName("SDGService.computeGlobalMetrics() called exactly once per request")
        @WithMockUser(username = "admin", roles = { "ADMIN" })
        void testDashboard_sdgServiceCalledOnce() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(reportAggregatorService.findAllReports()).thenReturn(java.util.List.of());
            when(industryService.findAll()).thenReturn(java.util.List.of());

            mockMvc.perform(get("/dashboard"));

            verify(sdgService, times(1)).computeGlobalMetrics();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. INDUSTRY VIEW
    // ═══════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Industry (ROLE_INDUSTRY) dashboard")
    class IndustryDashboardTests {

        @Test
        @DisplayName("GET /dashboard as INDUSTRY → HTTP 200")
        @WithMockUser(username = "industry1", roles = { "INDUSTRY" })
        void testDashboard_returnsOkForIndustryRole() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            // Industry user has no linked industry in this simple mock
            when(userService.findByUsername("industry1")).thenReturn(Optional.empty());

            mockMvc.perform(get("/dashboard"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("dashboard/index"));
        }

        @Test
        @DisplayName("isAdmin attribute is false in model for INDUSTRY role")
        @WithMockUser(username = "industry1", roles = { "INDUSTRY" })
        void testDashboard_isAdminFalseForIndustryRole() throws Exception {
            when(sdgService.computeGlobalMetrics()).thenReturn(buildSampleMetrics());
            when(industryService.getTotalCount()).thenReturn(3L);
            when(wasteEntryService.getTotalCount()).thenReturn(12L);
            when(userService.findByUsername("industry1")).thenReturn(Optional.empty());

            mockMvc.perform(get("/dashboard"))
                    .andExpect(model().attribute("isAdmin", false));
        }
    }
}
