package com.plasticaudit.service;

import com.plasticaudit.dto.WasteStatsDTO;
import com.plasticaudit.repository.WasteEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds the WasteStatsDTO consumed by the /api/waste/stats REST endpoint.
 * Provides:
 * – Monthly waste trend (line chart)
 * – Per-industry comparison (bar chart)
 * – Global pie data (recycled vs eliminated vs remaining)
 */
@Service
public class WasteStatsService {

    private static final Logger log = LoggerFactory.getLogger(WasteStatsService.class);

    @Autowired
    private WasteEntryRepository wasteEntryRepository;

    /**
     * Builds the complete stats payload for the Chart.js dashboard.
     */
    public WasteStatsDTO buildStats() {
        log.info("[WasteStatsService] Building chart data");

        WasteStatsDTO dto = new WasteStatsDTO();

        // ── 1. Monthly trend ─────────────────────────────────────────
        List<Object[]> monthly = wasteEntryRepository.getMonthlyTrend();
        List<String> labels = new ArrayList<>();
        List<Double> generated = new ArrayList<>();
        List<Double> recycled = new ArrayList<>();

        for (Object[] row : monthly) {
            int year = toInt(row[0]);
            int month = toInt(row[1]);
            double gen = toDouble(row[2]);
            double rec = toDouble(row[3]);

            String label = Month.of(month).getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + year;
            labels.add(label);
            generated.add(gen);
            recycled.add(rec);
        }
        dto.setMonthLabels(labels);
        dto.setMonthlyGenerated(generated);
        dto.setMonthlyRecycled(recycled);

        // ── 2. Per-industry totals ────────────────────────────────────
        List<Object[]> industryRows = wasteEntryRepository.getIndustryTotals();
        List<String> industryNames = new ArrayList<>();
        List<Double> indGen = new ArrayList<>();
        List<Double> indRec = new ArrayList<>();

        for (Object[] row : industryRows) {
            industryNames.add((String) row[0]);
            indGen.add(toDouble(row[1]));
            indRec.add(toDouble(row[2]));
        }
        dto.setIndustryNames(industryNames);
        dto.setIndustryGenerated(indGen);
        dto.setIndustryRecycled(indRec);

        // ── 3. Global pie totals ──────────────────────────────────────
        List<Object[]> globalRows = wasteEntryRepository.getGlobalSdgMetrics();
        if (!globalRows.isEmpty()) {
            Object[] g = globalRows.get(0);
            dto.setGlobalGenerated(toDouble(g[0]));
            dto.setGlobalRecycled(toDouble(g[1]));
            dto.setGlobalEliminated(toDouble(g[2]));
        }

        log.info("[WasteStatsService] Stats built: {} monthly points, {} industries",
                labels.size(), industryNames.size());
        return dto;
    }

    // ── helpers ───────────────────────────────────────────────────
    private int toInt(Object o) {
        return o == null ? 0 : ((Number) o).intValue();
    }

    private double toDouble(Object o) {
        return o == null ? 0.0 : ((Number) o).doubleValue();
    }
}
