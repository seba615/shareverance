package com.shareverance.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.model.Organization;
import com.shareverance.service.OrganizationService;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<Organization> createOrganization(@RequestBody OrganizationRequest request,
                                                           HttpServletRequest httpRequest) {
        Organization created = organizationService.createOrganization(request, (Long) httpRequest.getAttribute("authenticatedUserId"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Organization>> getAllOrganizations(HttpServletRequest request) {
        return ResponseEntity.ok(organizationService.getOrganizationsForUser(
                (Long) request.getAttribute("authenticatedUserId")));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Organization> getOrganizationById(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.getOrganizationById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Organization> updateOrganization(@PathVariable Long id, @RequestBody OrganizationRequest request) {
        return ResponseEntity.ok(organizationService.updateOrganization(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Organization> deactivateOrganization(@PathVariable Long id) {
        return ResponseEntity.ok(organizationService.deactivateOrganization(id));
    }
}
