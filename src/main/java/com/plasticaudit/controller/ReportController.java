package com.plasticaudit.controller;

import com.plasticaudit.dto.ReportRecommendationDTO;
import com.plasticaudit.entity.AuditReport;
import com.plasticaudit.entity.User;
import com.plasticaudit.service.IndustryService;
import com.plasticaudit.service.RecommendationService;
import com.plasticaudit.service.PdfReportService;
import com.plasticaudit.service.ReportAggregatorService;
import com.plasticaudit.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CO2/CO3/CO1 — Report Controller.
 * Triggers the async multithreaded report aggregator and passes
 * ReportRecommendationDTOs (with rule-based recommendations)
 * to the view layer.
 */
@Controller
@RequestMapping("/reports")
public class ReportController {

        private static final Logger log = LoggerFactory.getLogger(ReportController.class);

        @Autowired
        private ReportAggregatorService reportAggregatorService;
        @Autowired
        private UserService userService;
        @Autowired
        private IndustryService industryService;
        @Autowired
        private RecommendationService recommendationService;
        @Autowired
        private PdfReportService pdfReportService;
        @Autowired
        private com.plasticaudit.repository.AuditReportRepository auditReportRepository;

        @GetMapping
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public String myReports(@AuthenticationPrincipal UserDetails currentUser, Model model) {
                boolean isAdmin = currentUser.getAuthorities().stream()
                                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

                List<AuditReport> reports = new ArrayList<>();

                if (isAdmin) {
                        reports = reportAggregatorService.findAllReports();
                        model.addAttribute("industries", industryService.findAll());
                } else {
                        final List<AuditReport> industryReports = new ArrayList<>();
                        userService.findByUsername(currentUser.getUsername()).ifPresent(u -> {
                                if (u.getIndustry() != null) {
                                        industryReports.addAll(
                                                        reportAggregatorService.findReportsByIndustry(
                                                                        u.getIndustry().getId()));
                                }
                        });
                        reports = industryReports;
                }

                // Build recommendation DTOs — each report paired with its recommendations
                List<ReportRecommendationDTO> reportDTOs = reports.stream()
                                .map(r -> new ReportRecommendationDTO(r, recommendationService.generateFor(r)))
                                .collect(Collectors.toList());

                log.info("[RecommendationEngine] Built {} report DTOs for user '{}'",
                                reportDTOs.size(), currentUser.getUsername());

                model.addAttribute("reports", reports); // kept for backward compat
                model.addAttribute("reportDTOs", reportDTOs); // primary model for template
                model.addAttribute("isAdmin", isAdmin);
                return "reports/view";
        }

        @GetMapping("/generate")
        @PreAuthorize("hasRole('ADMIN')")
        public String showGenerateForm(Model model) {
                model.addAttribute("industries", industryService.findAll());
                model.addAttribute("today", LocalDate.now());
                model.addAttribute("monthAgo", LocalDate.now().minusMonths(1));
                return "reports/generate";
        }

        /**
         * CO1 — Triggers multithreaded @Async report aggregation for all industries.
         */
        @PostMapping("/generate")
        @PreAuthorize("hasRole('ADMIN')")
        public String generateReports(
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
                        @AuthenticationPrincipal UserDetails currentUser,
                        RedirectAttributes redirectAttributes) {

                User user = userService.findByUsername(currentUser.getUsername())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                CompletableFuture<List<AuditReport>> future = reportAggregatorService
                                .aggregateReportsForAllIndustries(periodStart, periodEnd, user);

                future.thenAccept(reports -> log.info(
                                "[CO1] Async aggregation complete: {} reports generated", reports.size()));

                redirectAttributes.addFlashAttribute("successMsg",
                                "Report generation started for period " + periodStart + " to " + periodEnd +
                                                ". Check back in a moment for results.");
                return "redirect:/reports";
        }

        @PostMapping("/status/{id}")
        @PreAuthorize("hasRole('ADMIN')")
        public String updateStatus(@PathVariable Long id,
                        @RequestParam String status,
                        RedirectAttributes redirectAttributes) {
                reportAggregatorService.updateReportStatus(id, AuditReport.ReportStatus.valueOf(status));
                redirectAttributes.addFlashAttribute("successMsg", "Report status updated.");
                return "redirect:/reports";
        }

        @GetMapping("/{id}/download")
        @org.springframework.transaction.annotation.Transactional(readOnly = true)
        public org.springframework.http.ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
                log.info("[ReportController] PDF download request for report #{}", id);
                AuditReport report = auditReportRepository.findByIdWithDetails(id)
                                .orElseThrow(() -> new RuntimeException("Report not found"));

                byte[] pdfBytes = pdfReportService.generateReportPdf(report);
                if (pdfBytes == null) {
                        return org.springframework.http.ResponseEntity.internalServerError().build();
                }

                org.springframework.http.ContentDisposition contentDisposition = org.springframework.http.ContentDisposition
                                .attachment()
                                .filename("PlasticAudit_Report_" + id + ".pdf")
                                .build();

                return org.springframework.http.ResponseEntity.ok()
                                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                                                contentDisposition.toString())
                                .header(org.springframework.http.HttpHeaders.CACHE_CONTROL,
                                                "no-cache, no-store, must-revalidate")
                                .header(org.springframework.http.HttpHeaders.PRAGMA, "no-cache")
                                .header(org.springframework.http.HttpHeaders.EXPIRES, "0")
                                .contentLength(pdfBytes.length)
                                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                                .body(pdfBytes);
        }
}
