/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.api;

import de.uni_passau.fim.se2.application.service.AdminAuditService;
import de.uni_passau.fim.se2.application.service.ResearchExportService;
import de.uni_passau.fim.se2.application.service.ResearchLogService;
import de.uni_passau.fim.se2.domain.entities.AdminAuditLogDTO;
import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.entities.ResearchDataset;
import de.uni_passau.fim.se2.domain.entities.ResearchLog;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * The research log viewer behind {@code /admin/research}.
 *
 * <p>Everything under {@code /api/admin/**} is ADMIN-only through SecurityConfig, so there is
 * no annotation here — the same arrangement as the other four admin controllers.
 *
 * <p>Rows leave with user and class ids as UUIDs and nothing else identifying, matching the
 * pseudonymisation the research ZIP already promises. Turning a UUID into a name is what the
 * class pages are for, and they check class ownership before doing it.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/research")
@RequiredArgsConstructor
public class AdminResearchController {

    private final ResearchLogService researchLogService;
    private final ResearchExportService researchExportService;
    private final ClassRepository classRepository;
    private final AdminAuditService auditService;

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** One page of a dataset, filtered. */
    @GetMapping("/{dataset}")
    public ResponseEntity<ResearchLog.Page> getPage(
            @PathVariable String dataset,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "50") Integer size) {

        ResearchDataset ds = ResearchDataset.fromWireName(dataset);
        ResearchLog.Filter filter =
                researchLogService.buildFilter(ds, classId, eventType, from, to, page, size);
        return ResponseEntity.ok(researchLogService.page(ds, filter));
    }

    /** Counts for the strip above the table, over the same filter. */
    @GetMapping("/{dataset}/summary")
    public ResponseEntity<ResearchLog.Summary> getSummary(
            @PathVariable String dataset,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        ResearchDataset ds = ResearchDataset.fromWireName(dataset);
        ResearchLog.Filter filter =
                researchLogService.buildFilter(ds, classId, eventType, from, to, 0, 1);
        return ResponseEntity.ok(researchLogService.summary(ds, filter));
    }

    /**
     * The current filter as a CSV, every matching row rather than the visible page.
     *
     * <p>Written straight to the response like the other admin downloads: the result can be
     * large, and buffering it into a {@code ResponseEntity} to set Content-Length would mean
     * holding the whole export in memory to save the client a progress bar.
     */
    @GetMapping("/{dataset}/export.csv")
    public void downloadCsv(
            @PathVariable String dataset,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UUID adminId,
            HttpServletRequest req,
            HttpServletResponse resp) throws Exception {

        ResearchDataset ds = ResearchDataset.fromWireName(dataset);
        ResearchLog.Filter filter =
                researchLogService.buildFilter(ds, classId, eventType, from, to, 0, 1);

        String filename = "codeowl-" + ds.wireName() + "-" + slug(classId)
                + LocalDateTime.now().format(FILE_TS) + ".csv";
        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType("text/csv; charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        try {
            long rows = researchLogService.streamCsv(ds, filter, resp.getOutputStream());
            auditService.logSuccess(adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT_CSV,
                    req.getRemoteAddr(), req.getHeader("User-Agent"), null, rows);
        } catch (Exception e) {
            log.error("Research CSV export failed for admin={}: {}", adminId, e.getMessage());
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT_CSV,
                    req.getRemoteAddr(), req.getHeader("User-Agent"), e.getMessage());
            throw e;
        }
    }

    /** All four datasets for one class, bundled with the roster and a metadata file. */
    @GetMapping("/export.zip")
    public void downloadClassZip(
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UUID adminId,
            HttpServletRequest req,
            HttpServletResponse resp) throws Exception {

        // The filter is built against EVENTS because that is the only dataset an eventType can
        // apply to; the export narrows the other three by class and date alone.
        ResearchLog.Filter filter = researchLogService.buildFilter(
                ResearchDataset.EVENTS, classId, eventType, from, to, 0, 1);

        String className = classId == null ? null
                : classRepository.findById(classId).map(ClassDTO::getName).orElse(null);

        String filename = "codeowl-research-" + slug(classId)
                + LocalDateTime.now().format(FILE_TS) + ".zip";
        resp.setStatus(HttpStatus.OK.value());
        resp.setContentType("application/zip");
        resp.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        String adminShort = adminId != null ? adminId.toString().substring(0, 8) : "unknown";
        try {
            long rows = researchExportService.streamClassExport(
                    resp.getOutputStream(), classId, className, filter, adminShort);
            auditService.logSuccess(adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT_CLASS,
                    req.getRemoteAddr(), req.getHeader("User-Agent"), null, rows);
        } catch (Exception e) {
            log.error("Class research export failed for admin={}: {}", adminId, e.getMessage());
            auditService.logFailure(adminId, AdminAuditLogDTO.Action.RESEARCH_EXPORT_CLASS,
                    req.getRemoteAddr(), req.getHeader("User-Agent"), e.getMessage());
            throw e;
        }
    }

    /**
     * Short class id for the filename, so two downloads taken in the same second for different
     * classes do not land on top of each other. The class name itself stays out of it — it is
     * free text and would have to be sanitised into a header.
     */
    private String slug(UUID classId) {
        return classId != null ? classId.toString().substring(0, 8) + "-" : "all-";
    }
}
