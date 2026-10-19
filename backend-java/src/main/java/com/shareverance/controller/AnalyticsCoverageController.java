package com.shareverance.controller;

import com.shareverance.service.AnalyticsCoverageService;
import com.shareverance.service.AnalyticsCoverageService.Coverage;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/analytics/coverage")
public class AnalyticsCoverageController {
    private final AnalyticsCoverageService service;
    public AnalyticsCoverageController(AnalyticsCoverageService service) { this.service = service; }

    @GetMapping
    public List<Coverage> list(@PathVariable Long organizationId, HttpServletRequest request) {
        return service.list(organizationId, (Long) request.getAttribute("authenticatedUserId"));
    }

    @PutMapping("/{month}")
    public Coverage declare(@PathVariable Long organizationId, @PathVariable String month, HttpServletRequest request) {
        return service.declare(organizationId, (Long) request.getAttribute("authenticatedUserId"), month, true);
    }

    @DeleteMapping("/{month}")
    public Coverage withdraw(@PathVariable Long organizationId, @PathVariable String month, HttpServletRequest request) {
        return service.declare(organizationId, (Long) request.getAttribute("authenticatedUserId"), month, false);
    }
}
