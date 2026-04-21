package com.plasticaudit.controller;

import com.plasticaudit.dto.WasteStatsDTO;
import com.plasticaudit.service.IndustryService;
import com.plasticaudit.service.UserService;
import com.plasticaudit.service.WasteStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WasteStatsController.class)
@DisplayName("WasteStatsController — API Tests")
class WasteStatsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WasteStatsService wasteStatsService;

    @MockBean
    private UserService userService;

    @MockBean
    private IndustryService industryService;

    @Test
    @DisplayName("GET /charts — 200 OK for authenticated user")
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getChartsPage_returnsStatusOk() throws Exception {
        mockMvc.perform(get("/charts"))
                .andExpect(status().isOk())
                .andExpect(view().name("charts/index"));
    }

    @Test
    @DisplayName("GET /api/waste/stats — returns JSON with correct structure")
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void getWasteStats_returnsJsonStructure() throws Exception {
        // Arrange
        WasteStatsDTO dto = new WasteStatsDTO();
        dto.setMonthLabels(List.of("Jan 2026"));
        dto.setMonthlyGenerated(List.of(100.0));
        dto.setMonthlyRecycled(List.of(50.0));
        dto.setIndustryNames(List.of("Ind1"));
        dto.setIndustryGenerated(List.of(100.0));
        dto.setIndustryRecycled(List.of(50.0));
        dto.setGlobalGenerated(100.0);
        dto.setGlobalRecycled(50.0);
        dto.setGlobalEliminated(10.0);

        when(wasteStatsService.buildStats()).thenReturn(dto);

        // Act & Assert
        mockMvc.perform(get("/api/waste/stats"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.monthLabels[0]").value("Jan 2026"))
                .andExpect(jsonPath("$.monthlyGenerated[0]").value(100.0))
                .andExpect(jsonPath("$.globalGenerated").value(100.0))
                .andExpect(jsonPath("$.globalRecycled").value(50.0))
                .andExpect(jsonPath("$.globalEliminated").value(10.0));
    }

    @Test
    @DisplayName("GET /api/waste/stats — 403 Forbidden for unauthenticated users")
    void getWasteStats_unauthenticated_returnsError() throws Exception {
        mockMvc.perform(get("/api/waste/stats"))
                .andExpect(status().is4xxClientError());
    }
}
