package com.plasticaudit.controller;

import com.plasticaudit.dto.WasteStatsDTO;
import com.plasticaudit.service.WasteStatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Provides the Chart.js data API and the charts Thymeleaf view.
 *
 * GET /charts → renders charts/index.html (Thymeleaf page)
 * GET /api/waste/stats → returns WasteStatsDTO as JSON (Chart.js fetch target)
 */
@Controller
@RequestMapping
public class WasteStatsController {

    private static final Logger log = LoggerFactory.getLogger(WasteStatsController.class);

    @Autowired
    private WasteStatsService wasteStatsService;

    /** Render the Chart.js dashboard page. */
    @GetMapping("/charts")
    @PreAuthorize("isAuthenticated()")
    public String chartsPage() {
        return "charts/index";
    }

    /** REST endpoint consumed by Chart.js via fetch(). */
    @GetMapping("/api/waste/stats")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<?> getWasteStats() {
        log.info("[WasteStatsController] GET /api/waste/stats");
        try {
            WasteStatsDTO stats = wasteStatsService.buildStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("[WasteStatsController] Error building stats", e);
            return ResponseEntity.internalServerError()
                    .body(java.util.Map.of("error", "Failed to load chart data", "message", e.getMessage()));
        }
    }
}
