package com.shareverance.controller;

import org.springframework.beans.factory.annotation.Autowired;
import com.shareverance.dto.OcrResultResponse;
import com.shareverance.dto.ReceiptResponse;
import com.shareverance.dto.ReceiptUploadResponse;
import com.shareverance.service.AuthService;
import com.shareverance.service.OcrService;
import com.shareverance.service.ReceiptService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/receipts")
public class ReceiptController {

    private final ReceiptService receiptService;
    private final AuthService authService;
    private final OcrService ocrService;

    @Autowired
    public ReceiptController(ReceiptService receiptService, AuthService authService, OcrService ocrService) {
        this.receiptService = receiptService;
        this.authService = authService;
        this.ocrService = ocrService;
    }

    public ReceiptController(ReceiptService receiptService, AuthService authService) {
        this(receiptService, authService, null);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptUploadResponse> uploadReceipt(
            @PathVariable Long organizationId,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (userId == null || !authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        ReceiptUploadResponse response = receiptService.storeReceipt(organizationId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ReceiptResponse>> listReceipts(
            @PathVariable Long organizationId,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (userId == null || !authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        List<ReceiptResponse> receipts = receiptService.getReceiptsByOrganization(organizationId, userId);
        return ResponseEntity.ok(receipts);
    }

    @GetMapping("/{receiptId}")
    public ResponseEntity<Resource> downloadReceipt(
            @PathVariable Long organizationId,
            @PathVariable String receiptId,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (userId == null || !authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        ReceiptResponse metadata = receiptService.getReceiptMetadata(organizationId, receiptId, userId);
        Resource resource = receiptService.loadReceiptFile(organizationId, receiptId, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(metadata.originalFilename(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.fileSizeBytes())
                .body(resource);
    }

    @GetMapping("/{receiptId}/metadata")
    public ResponseEntity<ReceiptResponse> getReceiptMetadata(
            @PathVariable Long organizationId,
            @PathVariable String receiptId,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (userId == null || !authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        ReceiptResponse metadata = receiptService.getReceiptMetadata(organizationId, receiptId, userId);
        return ResponseEntity.ok(metadata);
    }

    @PostMapping("/{receiptId}/ocr")
    public ResponseEntity<OcrResultResponse> processReceiptOcr(
            @PathVariable Long organizationId,
            @PathVariable String receiptId,
            HttpServletRequest httpRequest) {

        Long userId = (Long) httpRequest.getAttribute("authenticatedUserId");
        if (userId == null || !authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        OcrResultResponse ocrResult = ocrService.processReceiptOcr(organizationId, receiptId, userId);
        return ResponseEntity.ok(ocrResult);
    }
}
