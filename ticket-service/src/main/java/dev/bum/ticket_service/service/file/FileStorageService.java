package dev.bum.ticket_service.service.file;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path uploadRoot;
    private final String publicUrlPrefix;

    public FileStorageService(
            @Value("${app.upload.root:uploads}") String uploadRoot,
            @Value("${app.upload.public-url-prefix:/ticket/uploads}") String publicUrlPrefix
    ) {
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
        this.publicUrlPrefix = trimTrailingSlash(publicUrlPrefix);
    }

    public String saveEventPoster(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String extension = extractExtension(file.getOriginalFilename());
        validateImage(file, extension);

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path posterDir = uploadRoot.resolve("events").resolve("posters").normalize();
        Path target = posterDir.resolve(storedFileName).normalize();

        if (!target.startsWith(posterDir)) {
            throw new IllegalArgumentException("Invalid file path.");
        }

        try {
            Files.createDirectories(posterDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store event poster.", e);
        }

        return publicUrlPrefix + "/events/posters/" + storedFileName;
    }

    private void validateImage(MultipartFile file, String extension) {
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Only jpg, jpeg, png, webp image files are allowed.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }
    }

    private String extractExtension(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename != null ? originalFilename : "");
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("File extension is required.");
        }

        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
