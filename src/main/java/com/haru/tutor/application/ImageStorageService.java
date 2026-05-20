package com.haru.tutor.application;

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
import java.util.Set;
import java.util.UUID;

@Service
public class ImageStorageService {

    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final Path uploadRoot;

    public ImageStorageService(@Value("${haru.upload-dir:uploads}") String uploadDir) {
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    public String storeTutorProfileImage(Long userId, MultipartFile file) {
        validate(file);

        String extension = extensionFor(file.getContentType());
        Path targetDirectory = uploadRoot.resolve("tutors").resolve(String.valueOf(userId)).normalize();
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
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Image upload failed.");
        }

        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/uploads/tutors/")
                .path(String.valueOf(userId))
                .path("/")
                .path(target.getFileName().toString())
                .toUriString();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Image file is required.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Image file must be 5MB or smaller.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Only JPG, PNG, WebP, or GIF images can be uploaded.");
        }
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST, "Unsupported image type.");
        };
    }
}
