package com.shareverance.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.shareverance.dto.MembershipRequest;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.UserOrganization;
import com.shareverance.service.MembershipService;
import com.shareverance.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/memberships")
public class MembershipController {

    private final MembershipService membershipService;
    private final AuthService authService;

    public MembershipController(MembershipService membershipService, AuthService authService) {
        this.membershipService = membershipService;
        this.authService = authService;
    }

    @PostMapping
    public ResponseEntity<UserOrganization> addMember(@RequestBody MembershipRequest request,
                                                      HttpServletRequest httpRequest) {
        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (!authService.isOrganizationAdmin(userId, request.organizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Se requiere rol ADMIN");
        }
        UserOrganization created = membershipService.addMemberToOrganization(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserOrganization> getMembershipById(@PathVariable Long id) {
        return ResponseEntity.ok(membershipService.getMembershipById(id));
    }

    @GetMapping("/organization/{organizationId}")
    public ResponseEntity<List<UserOrganization>> getMembershipsByOrganization(@PathVariable Long organizationId) {
        return ResponseEntity.ok(membershipService.getMembershipsByOrganization(organizationId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserOrganization>> getMembershipsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(membershipService.getMembershipsByUser(userId));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserOrganization> updateRole(@PathVariable Long id, @RequestParam MembershipRole role) {
        return ResponseEntity.ok(membershipService.updateMemberRole(id, role));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<UserOrganization> deactivateMembership(@PathVariable Long id) {
        return ResponseEntity.ok(membershipService.deactivateMembership(id));
    }
}
