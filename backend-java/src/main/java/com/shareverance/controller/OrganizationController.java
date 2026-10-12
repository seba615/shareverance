package com.shareverance.controller;

import com.shareverance.dto.OrganizationRequest;
import com.shareverance.model.Organization;
import com.shareverance.service.OrganizationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    public ResponseEntity<Organization> createOrganization(@RequestBody OrganizationRequest request) {
        Organization created = organizationService.createOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<List<Organization>> getAllOrganizations() {
        return ResponseEntity.ok(organizationService.getAllOrganizations());
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

