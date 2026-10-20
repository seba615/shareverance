package com.shareverance.controller;

import com.shareverance.service.AnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/analytics")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    @GetMapping("/monthly")
    public AnalyticsService.Response monthly(@PathVariable Long organizationId,
            @RequestParam(required = false) String fromMonth,
            @RequestParam(required = false) String toMonth, HttpServletRequest request) {
        return service.monthly(organizationId, (Long) request.getAttribute("authenticatedUserId"), fromMonth, toMonth);
    }
}
