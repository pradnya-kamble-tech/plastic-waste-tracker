package com.plasticaudit.controller;

import com.plasticaudit.service.IndustryService;
import com.plasticaudit.service.ReportAggregatorService;
import com.plasticaudit.service.SDGService;
import com.plasticaudit.service.UserService;
import com.plasticaudit.service.WasteEntryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * CO2/CO3 — Dashboard Controller.
 * Delegates SDG metric computation to SDGService (clean MVC separation).
 */
@Controller
@RequestMapping("/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    @Autowired
    private IndustryService industryService;
    @Autowired
    private WasteEntryService wasteEntryService;
    @Autowired
    private UserService userService;
    @Autowired
    private ReportAggregatorService reportAggregatorService;
    @Autowired
    private SDGService sdgService; // ← new: delegates SDG calculations

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String dashboard(@AuthenticationPrincipal UserDetails currentUser, Model model) {
        log.info("[Dashboard] Loading dashboard for user: {}", currentUser.getUsername());

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        model.addAttribute("currentUser", currentUser.getUsername());
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("totalIndustries", industryService.getTotalCount());
        model.addAttribute("totalEntries", wasteEntryService.getTotalCount());

        // ── SDG Impact Metrics — delegated to SDGService ───────────────────
        SDGService.SDGMetrics sdg = sdgService.computeGlobalMetrics();
        model.addAttribute("sdg", sdg);

        // Also expose formatted strings for backward compatibility
        model.addAttribute("sdgGenerated", String.format("%.2f", sdg.getTotalGeneratedKg()));
        model.addAttribute("sdgRecycled", String.format("%.2f", sdg.getTotalRecycledKg()));
        model.addAttribute("sdgEliminated", String.format("%.2f", sdg.getTotalEliminatedKg()));
        model.addAttribute("sdgReductionRate", String.format("%.1f", sdg.getReductionRate()));
        model.addAttribute("sdgRecyclingRatio", String.format("%.1f", sdg.getRecyclingRatio()));

        if (isAdmin) {
            model.addAttribute("recentReports", reportAggregatorService.findAllReports());
            model.addAttribute("allIndustries", industryService.findAll());
        } else {
            userService.findByUsername(currentUser.getUsername()).ifPresent(u -> {
                if (u.getIndustry() != null) {
                    long industryId = u.getIndustry().getId();
                    model.addAttribute("userIndustry", u.getIndustry());
                    model.addAttribute("myEntries",
                            wasteEntryService.findByIndustryId(industryId));
                    model.addAttribute("myReports",
                            reportAggregatorService.findReportsByIndustry(industryId));

                    // Industry-specific SDG metrics
                    double[] summary = wasteEntryService.getIndustrySummaryRaw(industryId);
                    SDGService.SDGMetrics industrySdg = sdgService.computeMetricsFrom(summary[0], summary[1],
                            summary[2]);
                    model.addAttribute("industrySdg", industrySdg);
                }
            });
        }

        log.debug("[Dashboard] SDG metrics loaded — ReductionRate={}%, EnvScore={}",
                String.format("%.1f", sdg.getReductionRate()),
                String.format("%.1f", sdg.getEnvironmentalScore()));

        return "dashboard/index";
    }
}
