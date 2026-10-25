package com.shareverance.controller;

import com.shareverance.dto.IndividualSummaryResponse;
import com.shareverance.service.IndividualSummaryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/expenses/my-summary")
public class IndividualSummaryController {
    private final IndividualSummaryService service;
    public IndividualSummaryController(IndividualSummaryService service) { this.service = service; }
    @GetMapping
    public IndividualSummaryResponse get(@PathVariable Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long membershipId,
            HttpServletRequest request) {
        return service.summarize((Long) request.getAttribute("authenticatedUserId"), organizationId, fromDate, toDate, membershipId);
    }
}
