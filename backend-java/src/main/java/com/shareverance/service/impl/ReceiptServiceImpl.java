package com.shareverance.service.impl;

import com.shareverance.dto.ReceiptResponse;
import com.shareverance.dto.ReceiptUploadResponse;
import com.shareverance.model.Receipt;
import com.shareverance.repository.ReceiptRepository;
import com.shareverance.service.AuthService;
import com.shareverance.service.ReceiptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReceiptServiceImpl implements ReceiptService {

    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47,
            (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A
    };

    private final ReceiptRepository receiptRepository;
    private final AuthService authService;
    private final String storagePath;
    private final long maxFileSizeBytes;
    private final long maxPixels;

    public ReceiptServiceImpl(
            ReceiptRepository receiptRepository,
            AuthService authService,
            @Value("${receipts.storage-path:/data/receipts}") String storagePath,
            @Value("${receipts.max-file-size-bytes:10485760}") long maxFileSizeBytes,
            @Value("${receipts.max-pixels:50000000}") long maxPixels) {
        this.receiptRepository = receiptRepository;
        this.authService = authService;
        this.storagePath = storagePath;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxPixels = maxPixels;
    }

    @Override
    public ReceiptUploadResponse storeReceipt(Long organizationId, Long userId, MultipartFile file) {
        if (organizationId == null || userId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Organización y usuario son obligatorios");
        }
        if (!authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo está vacío");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "El archivo supera el tamaño máximo permitido de 10 MiB");
        }

        byte[] headerBytes = new byte[8];
        byte[] fullContent;
        try {
            fullContent = file.getBytes();
            if (fullContent.length < 8) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo es demasiado pequeño o está corrupto");
            }
            System.arraycopy(fullContent, 0, headerBytes, 0, 8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Error al leer el contenido del archivo", e);
        }

        String detectedMimeType;
        String extension;
        if (isJpeg(headerBytes)) {
            detectedMimeType = "image/jpeg";
            extension = ".jpg";
        } else if (isPng(headerBytes)) {
            detectedMimeType = "image/png";
            extension = ".png";
        } else {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Formato de imagen no soportado. Solo se admiten archivos JPEG y PNG");
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(fullContent));
            if (image == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La imagen está corrupta o no pudo ser decodificada");
            }
            long pixels = (long) image.getWidth() * (long) image.getHeight();
            if (pixels > maxPixels) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Las dimensiones de la imagen superan el límite permitido");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo procesar la imagen enviada", e);
        }

        Path root = Paths.get(storagePath).toAbsolutePath().normalize();
        Path orgDir = root.resolve("org_" + organizationId).normalize();
        if (!orgDir.startsWith(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ruta de almacenamiento inválida");
        }

        try {
            Files.createDirectories(orgDir);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al preparar el almacenamiento", e);
        }

        String receiptId = UUID.randomUUID().toString();
        String storageFileName = receiptId + extension;
        Path destination = orgDir.resolve(storageFileName).normalize();
        if (!destination.startsWith(orgDir)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre de archivo no permitido");
        }

        try {
            Files.write(destination, fullContent);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al persistir el archivo en disco", e);
        }

        String rawOriginalFilename = file.getOriginalFilename();
        String sanitizedOriginalFilename = rawOriginalFilename != null
                ? Paths.get(rawOriginalFilename).getFileName().toString()
                : storageFileName;

        String relativePath = "org_" + organizationId + "/" + storageFileName;
        Receipt receipt = new Receipt(
                receiptId,
                organizationId,
                userId,
                relativePath,
                sanitizedOriginalFilename,
                detectedMimeType,
                file.getSize(),
                null,
                Instant.now(),
                true
        );

        Receipt saved = receiptRepository.save(receipt);

        return new ReceiptUploadResponse(
                saved.getId(),
                saved.getOrganizationId(),
                saved.getOriginalFilename(),
                saved.getContentType(),
                saved.getFileSizeBytes(),
                saved.getCreatedAt(),
                "STORED"
        );
    }

    @Override
    public Resource loadReceiptFile(Long organizationId, String receiptId, Long userId) {
        Receipt receipt = getAuthorizedReceipt(organizationId, receiptId, userId);
        Path root = Paths.get(storagePath).toAbsolutePath().normalize();
        Path filePath = root.resolve(receipt.getStoragePath()).normalize();

        if (!filePath.startsWith(root) || !Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El archivo del comprobante no existe o no es accesible");
        }

        return new FileSystemResource(filePath.toFile());
    }

    @Override
    public ReceiptResponse getReceiptMetadata(Long organizationId, String receiptId, Long userId) {
        Receipt receipt = getAuthorizedReceipt(organizationId, receiptId, userId);
        return mapToResponse(receipt);
    }

    @Override
    public List<ReceiptResponse> getReceiptsByOrganization(Long organizationId, Long userId) {
        if (!authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }
        boolean isAdmin = authService.isOrganizationAdmin(userId, organizationId);
        return receiptRepository.findByOrganizationId(organizationId).stream()
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .filter(r -> r.getExpenseId() != null || isAdmin || Objects.equals(r.getUploadedByUserId(), userId))
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void linkReceiptToExpense(String receiptId, Long expenseId, Long organizationId, Long userId) {
        if (receiptId == null || expenseId == null || organizationId == null) {
            return;
        }
        Receipt receipt = receiptRepository.findByIdAndOrganizationId(receiptId, organizationId)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));

        if (!receipt.getOrganizationId().equals(organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El comprobante pertenece a otra organización");
        }
        if (receipt.getExpenseId() != null && !receipt.getExpenseId().equals(expenseId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El comprobante ya está vinculado a otro gasto");
        }

        boolean isAdmin = authService.isOrganizationAdmin(userId, organizationId);
        if (!isAdmin && !Objects.equals(receipt.getUploadedByUserId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés permiso para asociar este comprobante");
        }

        receiptRepository.updateExpenseId(receiptId, expenseId);
    }

    private Receipt getAuthorizedReceipt(Long organizationId, String receiptId, Long userId) {
        if (!authService.isOrganizationMember(userId, organizationId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No pertenecés a esta organización");
        }

        Receipt receipt = receiptRepository.findByIdAndOrganizationId(receiptId, organizationId)
                .filter(r -> Boolean.TRUE.equals(r.getIsActive()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comprobante no encontrado"));

        if (receipt.getExpenseId() == null) {
            boolean isAdmin = authService.isOrganizationAdmin(userId, organizationId);
            if (!isAdmin && !Objects.equals(receipt.getUploadedByUserId(), userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenés permiso para acceder a este comprobante");
            }
        }
        return receipt;
    }

    private ReceiptResponse mapToResponse(Receipt receipt) {
        return new ReceiptResponse(
                receipt.getId(),
                receipt.getOrganizationId(),
                receipt.getUploadedByUserId(),
                receipt.getOriginalFilename(),
                receipt.getContentType(),
                receipt.getFileSizeBytes(),
                receipt.getExpenseId(),
                receipt.getCreatedAt()
        );
    }

    private static boolean isJpeg(byte[] header) {
        return header.length >= 3
                && header[0] == JPEG_MAGIC[0]
                && header[1] == JPEG_MAGIC[1]
                && header[2] == JPEG_MAGIC[2];
    }

    private static boolean isPng(byte[] header) {
        if (header.length < PNG_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if (header[i] != PNG_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
