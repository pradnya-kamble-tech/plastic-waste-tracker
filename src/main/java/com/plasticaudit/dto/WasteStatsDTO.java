package com.plasticaudit.dto;

import java.util.List;

/**
 * Response DTO for /api/waste/stats endpoint.
 * Consumed entirely by Chart.js via fetch() on the frontend.
 */
public class WasteStatsDTO {

    /** X-axis labels: "Jan 2025", "Feb 2025", … */
    private List<String> monthLabels;

    /** Monthly generated kg aligned with monthLabels */
    private List<Double> monthlyGenerated;

    /** Monthly recycled kg aligned with monthLabels */
    private List<Double> monthlyRecycled;

    /** Industry names for bar-chart X axis */
    private List<String> industryNames;

    /** Total generated per industry (bar-chart series 1) */
    private List<Double> industryGenerated;

    /** Total recycled per industry (bar-chart series 2) */
    private List<Double> industryRecycled;

    /**
     * Global totals for pie chart: [totalGenerated, totalRecycled, totalEliminated]
     */
    private double globalGenerated;
    private double globalRecycled;
    private double globalEliminated;

    // ── Constructor ──────────────────────────────────────────────
    public WasteStatsDTO() {
    }

    // ── Getters / Setters ────────────────────────────────────────

    public List<String> getMonthLabels() {
        return monthLabels;
    }

    public void setMonthLabels(List<String> monthLabels) {
        this.monthLabels = monthLabels;
    }

    public List<Double> getMonthlyGenerated() {
        return monthlyGenerated;
    }

    public void setMonthlyGenerated(List<Double> monthlyGenerated) {
        this.monthlyGenerated = monthlyGenerated;
    }

    public List<Double> getMonthlyRecycled() {
        return monthlyRecycled;
    }

    public void setMonthlyRecycled(List<Double> monthlyRecycled) {
        this.monthlyRecycled = monthlyRecycled;
    }

    public List<String> getIndustryNames() {
        return industryNames;
    }

    public void setIndustryNames(List<String> industryNames) {
        this.industryNames = industryNames;
    }

    public List<Double> getIndustryGenerated() {
        return industryGenerated;
    }

    public void setIndustryGenerated(List<Double> industryGenerated) {
        this.industryGenerated = industryGenerated;
    }

    public List<Double> getIndustryRecycled() {
        return industryRecycled;
    }

    public void setIndustryRecycled(List<Double> industryRecycled) {
        this.industryRecycled = industryRecycled;
    }

    public double getGlobalGenerated() {
        return globalGenerated;
    }

    public void setGlobalGenerated(double globalGenerated) {
        this.globalGenerated = globalGenerated;
    }

    public double getGlobalRecycled() {
        return globalRecycled;
    }

    public void setGlobalRecycled(double globalRecycled) {
        this.globalRecycled = globalRecycled;
    }

    public double getGlobalEliminated() {
        return globalEliminated;
    }

    public void setGlobalEliminated(double globalEliminated) {
        this.globalEliminated = globalEliminated;
    }
}
