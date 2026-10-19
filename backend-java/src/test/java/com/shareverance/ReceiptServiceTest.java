package com.shareverance;

import com.shareverance.dto.ReceiptResponse;
import com.shareverance.dto.ReceiptUploadResponse;
import com.shareverance.model.MembershipRole;
import com.shareverance.model.Organization;
import com.shareverance.model.Receipt;
import com.shareverance.model.User;
import com.shareverance.model.UserOrganization;
import com.shareverance.repository.mock.MockReceiptRepository;
import com.shareverance.service.AuthService;
import com.shareverance.service.impl.ReceiptServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class ReceiptServiceTest {

    private MockReceiptRepository receiptRepository;
    private AuthService authService;
    private ReceiptServiceImpl receiptService;

    @TempDir
    Path tempStorageDir;

    private byte[] validPngBytes;
    private byte[] validJpgBytes;

    @BeforeEach
    void setUp() throws IOException {
        receiptRepository = new MockReceiptRepository();
        authService = Mockito.mock(AuthService.class);
        receiptService = new ReceiptServiceImpl(
                receiptRepository,
                authService,
                tempStorageDir.toString(),
                10 * 1024 * 1024,
                50_000_000
        );

        BufferedImage img = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        ImageIO.write(img, "png", pngOut);
        validPngBytes = pngOut.toByteArray();

        ByteArrayOutputStream jpgOut = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", jpgOut);
        validJpgBytes = jpgOut.toByteArray();
    }

    @Test
    void storeReceipt_validPng_success() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);

        ReceiptUploadResponse response = receiptService.storeReceipt(10L, 1L, file);

        assertNotNull(response);
        assertNotNull(response.receiptId());
        assertEquals(10L, response.organizationId());
        assertEquals("ticket.png", response.originalFilename());
        assertEquals("image/png", response.contentType());
        assertEquals(validPngBytes.length, response.fileSizeBytes());
        assertEquals("STORED", response.status());

        Receipt stored = receiptRepository.findById(response.receiptId()).orElse(null);
        assertNotNull(stored);
        assertEquals(10L, stored.getOrganizationId());
        assertEquals(1L, stored.getUploadedByUserId());
        assertTrue(stored.getStoragePath().startsWith("org_10/"));
        assertTrue(stored.getStoragePath().endsWith(".png"));
    }

    @Test
    void storeReceipt_validJpeg_success() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "factura.jpg", "image/jpeg", validJpgBytes);

        ReceiptUploadResponse response = receiptService.storeReceipt(10L, 1L, file);

        assertNotNull(response);
        assertEquals("image/jpeg", response.contentType());
        assertTrue(response.originalFilename().endsWith(".jpg"));

        Receipt stored = receiptRepository.findById(response.receiptId()).orElse(null);
        assertNotNull(stored);
        assertTrue(stored.getStoragePath().endsWith(".jpg"));
    }

    @Test
    void storeReceipt_emptyFile_throwsBadRequest() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.storeReceipt(10L, 1L, file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void storeReceipt_exceedsMaxSize_throwsPayloadTooLarge() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        // 11 MiB
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", largeBytes);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.storeReceipt(10L, 1L, file));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, ex.getStatusCode());
    }

    @Test
    void storeReceipt_unsupportedFormat_throwsUnsupportedMediaType() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        byte[] fakePdf = "%PDF-1.4 simulated pdf document content".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", fakePdf);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.storeReceipt(10L, 1L, file));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getStatusCode());
    }

    @Test
    void storeReceipt_corruptedImageBytes_throwsBadRequest() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        // PNG header but corrupted body
        byte[] corrupt = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile(
                "file", "corrupted.png", "image/png", corrupt);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.storeReceipt(10L, 1L, file));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void storeReceipt_notMember_throwsForbidden() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.storeReceipt(10L, 1L, file));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void loadReceiptFile_and_getMetadata_success() throws IOException {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);
        ReceiptUploadResponse uploaded = receiptService.storeReceipt(10L, 1L, file);

        ReceiptResponse metadata = receiptService.getReceiptMetadata(10L, uploaded.receiptId(), 1L);
        assertNotNull(metadata);
        assertEquals(uploaded.receiptId(), metadata.receiptId());
        assertEquals("ticket.png", metadata.originalFilename());

        Resource resource = receiptService.loadReceiptFile(10L, uploaded.receiptId(), 1L);
        assertNotNull(resource);
        assertTrue(resource.exists());
        assertEquals(validPngBytes.length, resource.contentLength());
    }

    @Test
    void loadReceiptFile_temporaryNotOwnerAndNotAdmin_throwsForbidden() {
        when(authService.isOrganizationMember(2L, 10L)).thenReturn(true);
        when(authService.isOrganizationAdmin(2L, 10L)).thenReturn(false);
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);
        ReceiptUploadResponse uploaded = receiptService.storeReceipt(10L, 1L, file);

        // User 2 is member but not owner and not admin of unlinked receipt
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.loadReceiptFile(10L, uploaded.receiptId(), 2L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void loadReceiptFile_temporaryAdminCanAccess() throws IOException {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);
        when(authService.isOrganizationMember(2L, 10L)).thenReturn(true);
        when(authService.isOrganizationAdmin(2L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);
        ReceiptUploadResponse uploaded = receiptService.storeReceipt(10L, 1L, file);

        Resource resource = receiptService.loadReceiptFile(10L, uploaded.receiptId(), 2L);
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    @Test
    void loadReceiptFile_linkedToExpense_anyMemberCanAccess() throws IOException {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);
        when(authService.isOrganizationMember(2L, 10L)).thenReturn(true);
        when(authService.isOrganizationAdmin(2L, 10L)).thenReturn(false);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);
        ReceiptUploadResponse uploaded = receiptService.storeReceipt(10L, 1L, file);

        // Link to expense 100L
        receiptService.linkReceiptToExpense(uploaded.receiptId(), 100L, 10L, 1L);

        // Now user 2 can access
        Resource resource = receiptService.loadReceiptFile(10L, uploaded.receiptId(), 2L);
        assertNotNull(resource);
        assertTrue(resource.exists());
    }

    @Test
    void linkReceiptToExpense_alreadyLinkedToDifferentExpense_throwsConflict() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);

        MockMultipartFile file = new MockMultipartFile(
                "file", "ticket.png", "image/png", validPngBytes);
        ReceiptUploadResponse uploaded = receiptService.storeReceipt(10L, 1L, file);

        receiptService.linkReceiptToExpense(uploaded.receiptId(), 100L, 10L, 1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                receiptService.linkReceiptToExpense(uploaded.receiptId(), 200L, 10L, 1L));
        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    @Test
    void listReceipts_filtersProperly() {
        when(authService.isOrganizationMember(1L, 10L)).thenReturn(true);
        when(authService.isOrganizationMember(2L, 10L)).thenReturn(true);
        when(authService.isOrganizationAdmin(2L, 10L)).thenReturn(false);

        MockMultipartFile file1 = new MockMultipartFile("file", "t1.png", "image/png", validPngBytes);
        MockMultipartFile file2 = new MockMultipartFile("file", "t2.png", "image/png", validPngBytes);

        ReceiptUploadResponse up1 = receiptService.storeReceipt(10L, 1L, file1);
        ReceiptUploadResponse up2 = receiptService.storeReceipt(10L, 2L, file2);

        // User 2 only sees own unlinked receipt (up2), not up1
        List<ReceiptResponse> user2List = receiptService.getReceiptsByOrganization(10L, 2L);
        assertEquals(1, user2List.size());
        assertEquals(up2.receiptId(), user2List.get(0).receiptId());

        // Link up1 to expense
        receiptService.linkReceiptToExpense(up1.receiptId(), 50L, 10L, 1L);

        // Now user 2 sees both: linked up1 and own up2
        List<ReceiptResponse> user2ListAfter = receiptService.getReceiptsByOrganization(10L, 2L);
        assertEquals(2, user2ListAfter.size());
    }
}
