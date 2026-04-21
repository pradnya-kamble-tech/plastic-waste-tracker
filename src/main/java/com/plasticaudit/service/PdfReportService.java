package com.plasticaudit.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.plasticaudit.entity.AuditReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * PdfReportService — Generates PDF reports using OpenPDF.
 */
@Service
public class PdfReportService {

        private static final Logger log = LoggerFactory.getLogger(PdfReportService.class);

        @Autowired
        private SDGService sdgService;

        @Autowired
        private RecommendationService recommendationService;

        public byte[] generateReportPdf(AuditReport report) {
                log.info("[PdfService] Generating PDF for report #{}", report.getId());

                try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                        Document document = new Document(PageSize.A4);
                        PdfWriter.getInstance(document, out);

                        document.addTitle("Plastic Waste Audit Report");
                        document.addAuthor("PlasticAudit System");
                        document.addSubject("SDG 12 & 14 Compliance Audit");
                        document.addCreator("OpenPDF 1.3.40");
                        document.open();

                        // ── Header ──────────────────────────────────────────────────────
                        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.DARK_GRAY);
                        Paragraph title = new Paragraph("Plastic Waste Audit Report", titleFont);
                        title.setAlignment(Element.ALIGN_CENTER);
                        title.setSpacingAfter(10);
                        document.add(title);

                        Paragraph subtitle = new Paragraph("SDG 12 & 14 Sustainability Compliance",
                                        FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY));
                        subtitle.setAlignment(Element.ALIGN_CENTER);
                        subtitle.setSpacingAfter(20);
                        document.add(subtitle);

                        // ── Report Metadata ─────────────────────────────────────────────
                        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
                        document.add(new Paragraph("Industry: " + report.getIndustry().getName(), metaFont));
                        document.add(
                                        new Paragraph("Period: " + report.getPeriodStart() + " to "
                                                        + report.getPeriodEnd(), metaFont));
                        document.add(new Paragraph("Status: " + report.getStatus(), metaFont));
                        document.add(new Paragraph(
                                        "Generated: "
                                                        + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                                                        .format(java.time.LocalDateTime.now()),
                                        metaFont));
                        document.add(new Paragraph(" ", metaFont)); // spacer

                        // ── Waste Metrics Table ─────────────────────────────────────────
                        document.add(
                                        new Paragraph("1. Waste Generation Metrics",
                                                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                        document.add(new Paragraph(" ", metaFont));

                        PdfPTable wasteTable = new PdfPTable(3);
                        wasteTable.setWidthPercentage(100);
                        wasteTable.setSpacingBefore(10f);
                        wasteTable.setSpacingAfter(20f);

                        addTableHeader(wasteTable, "Generated (kg)");
                        addTableHeader(wasteTable, "Recycled (kg)");
                        addTableHeader(wasteTable, "Eliminated (kg)");

                        wasteTable.addCell(String.format("%.2f", report.getTotalGeneratedKg()));
                        wasteTable.addCell(String.format("%.2f", report.getTotalRecycledKg()));
                        wasteTable.addCell(String.format("%.2f", report.getTotalEliminatedKg()));
                        document.add(wasteTable);

                        // ── SDG Impact Section ──────────────────────────────────────────
                        SDGService.SDGMetrics sdg = sdgService.computeMetricsFrom(
                                        report.getTotalGeneratedKg(),
                                        report.getTotalRecycledKg(),
                                        report.getTotalEliminatedKg());

                        document.add(
                                        new Paragraph("2. SDG Impact Assessment",
                                                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                        document.add(new Paragraph(" ", metaFont));

                        PdfPTable sdgTable = new PdfPTable(3);
                        sdgTable.setWidthPercentage(100);
                        sdgTable.setSpacingBefore(10f);
                        sdgTable.setSpacingAfter(20f);

                        addTableHeader(sdgTable, "SDG 12 Score");
                        addTableHeader(sdgTable, "SDG 14 Score");
                        addTableHeader(sdgTable, "Env. Score (Grade)");

                        sdgTable.addCell(String.format("%.1f", sdg.getSdg12Score()));
                        sdgTable.addCell(String.format("%.1f", sdg.getSdg14Score()));
                        sdgTable.addCell(String.format("%.1f (%s)", sdg.getEnvironmentalScore(), sdg.getScoreGrade()));
                        document.add(sdgTable);

                        // ── Recommendations Section ─────────────────────────────────────
                        document.add(new Paragraph("3. Actionable Recommendations",
                                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
                        document.add(new Paragraph(" ", metaFont));

                        List<String> recommendations = recommendationService.generateFor(report);
                        com.lowagie.text.List pdfList = new com.lowagie.text.List(com.lowagie.text.List.UNORDERED);
                        pdfList.setListSymbol("• ");

                        for (String rec : recommendations) {
                                pdfList.add(new ListItem(rec, metaFont));
                        }
                        document.add(pdfList);

                        // ── Footer & Disclaimer ─────────────────────────────────────────
                        document.add(new Paragraph(" ", metaFont));
                        document.add(new Paragraph(" ", metaFont));

                        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, Color.GRAY);
                        document.add(new Paragraph(
                                        "Legal Disclaimer: This report is generated based on data submitted by the industry. PlasticAudit holds no responsibility for the accuracy of underlying data entries. All calculations follow SDG 12 & 14 frameworks.",
                                        footerFont));

                        document.add(new Paragraph("\nContact Support: support@plasticaudit.org | +1-800-RECYCLE",
                                        footerFont));
                        document.add(new Paragraph("\nReport generated by PlasticAudit System. Reference ID: "
                                        + java.util.UUID.randomUUID().toString(), footerFont));

                        document.close();
                        return out.toByteArray();
                } catch (DocumentException | java.io.IOException e) {
                        log.error("[PdfService] Error creating PDF", e);
                        throw new RuntimeException("Failed to generate PDF", e);
                }
        }

        private void addTableHeader(PdfPTable table, String columnTitle) {
                PdfPCell header = new PdfPCell();
                header.setBackgroundColor(new Color(22, 163, 74)); // Brand green
                header.setBorderWidth(1);
                header.setPhrase(new Phrase(columnTitle,
                                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE)));
                header.setPadding(5);
                header.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(header);
        }
}
