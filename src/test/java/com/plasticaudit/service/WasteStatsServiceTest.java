package com.plasticaudit.service;

import com.plasticaudit.dto.WasteStatsDTO;
import com.plasticaudit.repository.WasteEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WasteStatsService — Unit Tests")
class WasteStatsServiceTest {

    @Mock
    private WasteEntryRepository wasteEntryRepository;

    @InjectMocks
    private WasteStatsService wasteStatsService;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("buildStats — returns correctly formatted monthly data")
    void buildStats_returnsMonthlyData() {
        // Arrange
        List<Object[]> monthlyTrend = java.util.Arrays.<Object[]>asList(
                new Object[] { 2026, 1, 1000.0, 500.0 },
                new Object[] { 2026, 2, 1200.0, 600.0 });
        when(wasteEntryRepository.getMonthlyTrend()).thenReturn(monthlyTrend);
        when(wasteEntryRepository.getIndustryTotals()).thenReturn(Collections.emptyList());
        when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(Collections.emptyList());

        // Act
        WasteStatsDTO stats = wasteStatsService.buildStats();

        // Assert
        assertEquals(2, stats.getMonthLabels().size());
        assertEquals("Jan 2026", stats.getMonthLabels().get(0));
        assertEquals("Feb 2026", stats.getMonthLabels().get(1));
        assertEquals(1000.0, stats.getMonthlyGenerated().get(0));
        assertEquals(600.0, stats.getMonthlyRecycled().get(1));
    }

    @Test
    @DisplayName("buildStats — returns correctly aggregated industry data")
    void buildStats_returnsIndustryData() {
        // Arrange
        List<Object[]> industryTotals = java.util.Arrays.<Object[]>asList(
                new Object[] { "EcoFabric Ltd", 5000.0, 2500.0 },
                new Object[] { "GreenPack Solutions", 3000.0, 1500.0 });
        when(wasteEntryRepository.getMonthlyTrend()).thenReturn(Collections.emptyList());
        when(wasteEntryRepository.getIndustryTotals()).thenReturn(industryTotals);
        when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(Collections.emptyList());

        // Act
        WasteStatsDTO stats = wasteStatsService.buildStats();

        // Assert
        assertEquals(2, stats.getIndustryNames().size());
        assertEquals("EcoFabric Ltd", stats.getIndustryNames().get(0));
        assertEquals(5000.0, stats.getIndustryGenerated().get(0));
        assertEquals(1500.0, stats.getIndustryRecycled().get(1));
    }

    @Test
    @DisplayName("buildStats — handles empty data gracefully")
    void buildStats_handlesEmptyData() {
        // Arrange
        when(wasteEntryRepository.getMonthlyTrend()).thenReturn(Collections.emptyList());
        when(wasteEntryRepository.getIndustryTotals()).thenReturn(Collections.emptyList());
        when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(Collections.emptyList());

        // Act
        WasteStatsDTO stats = wasteStatsService.buildStats();

        // Assert
        assertTrue(stats.getMonthLabels().isEmpty());
        assertTrue(stats.getIndustryNames().isEmpty());
        assertEquals(0.0, stats.getGlobalGenerated());
    }

    @Test
    @DisplayName("buildStats — populates global metrics")
    void buildStats_populatesGlobalMetrics() {
        // Arrange
        List<Object[]> globalSdg = Collections.<Object[]>singletonList(
                new Object[] { 10000.0, 4000.0, 1000.0 });
        when(wasteEntryRepository.getMonthlyTrend()).thenReturn(Collections.emptyList());
        when(wasteEntryRepository.getIndustryTotals()).thenReturn(Collections.emptyList());
        when(wasteEntryRepository.getGlobalSdgMetrics()).thenReturn(globalSdg);

        // Act
        WasteStatsDTO stats = wasteStatsService.buildStats();

        // Assert
        assertEquals(10000.0, stats.getGlobalGenerated());
        assertEquals(4000.0, stats.getGlobalRecycled());
        assertEquals(1000.0, stats.getGlobalEliminated());
    }
}
