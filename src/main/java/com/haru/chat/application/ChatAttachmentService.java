package com.haru.chat.application;

import com.haru.chat.domain.MessageType;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Stores chat attachments on the upload volume under {@code uploads/chat/{roomId}/}.
 * Same mechanism as {@link com.haru.tutor.application.ImageStorageService}, with a
 * wider set of allowed content types. Files are served by the static
 * {@code /uploads/**} mapping; the random UUID file name is the only access gate,
 * so do not store sensitive documents here.
 */
@Service
public class ChatAttachmentService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.ofEntries(
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/png", ".png"),
            Map.entry("image/webp", ".webp"),
            Map.entry("image/gif", ".gif"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("text/plain", ".txt"),
            Map.entry("application/zip", ".zip"),
            Map.entry("application/msword", ".doc"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("application/vnd.ms-excel", ".xls"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.ms-powerpoint", ".ppt"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx")
    );

    public record StoredAttachment(
            String url,
            String originalName,
            String contentType,
            long size,
            MessageType messageType
    ) {
    }

    private final Path uploadRoot;

    public ChatAttachmentService(@Value("${haru.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public StoredAttachment store(Long roomId, MultipartFile file) {
        String contentType = validate(file);
        String extension = ALLOWED_CONTENT_TYPES.get(contentType);

        Path targetDirectory = uploadRoot.resolve("chat").resolve(String.valueOf(roomId)).normalize();
        Path target = targetDirectory.resolve(UUID.randomUUID() + extension).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Upload path is invalid.");
        }

        try {
            Files.createDirectories(targetDirectory);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Attachment upload failed.");
        }

        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/chat/")
                .path(String.valueOf(roomId))
                .path("/")
                .path(target.getFileName().toString())
                .toUriString();
        return new StoredAttachment(
                url,
                originalName(file),
                contentType,
                file.getSize(),
                contentType.startsWith("image/") ? MessageType.IMAGE : MessageType.FILE
        );
    }

    private String validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Attachment file is required.");
        }
        if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Attachment must be 5MB or smaller.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.containsKey(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "This file type cannot be attached.");
        }
        return contentType.toLowerCase(Locale.ROOT);
    }

    private String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "attachment";
        }
        // Strip any path segments a client may include.
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }
}
