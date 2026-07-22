package dev.bum.ticket_service.service.event.file;

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

    /**
     * 이벤트 포스터 이미지를 검증한 뒤 이벤트별 업로드 경로에 저장하고 공개 URL을 반환한다.
     */
    public String saveEventPoster(Long eventId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (eventId == null) {
            throw new IllegalArgumentException("이벤트 ID 값은 필수입니다.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        validateImage(file, extension);

        String storedFileName = UUID.randomUUID() + "." + extension;
        Path posterDir = uploadRoot.resolve("events").resolve("posters").resolve(String.valueOf(eventId)).normalize();
        Path target = posterDir.resolve(storedFileName).normalize();

        if (!target.startsWith(posterDir)) {
            throw new IllegalArgumentException("유효하지 않은 파일 경로입니다.");
        }

        try {
            Files.createDirectories(posterDir);
            file.transferTo(target);
        } catch (IOException e) {
            throw new IllegalStateException("이벤트 포스터 저장에 실패했습니다.", e);
        }

        return publicUrlPrefix + "/events/posters/" + eventId + "/" + storedFileName;
    }

    /**
     * 공개 URL이 업로드 경로에 속하면 연결된 포스터 파일을 삭제한다.
     */
    public void deleteByPublicUrl(String publicUrl) {
        if (!StringUtils.hasText(publicUrl) || !publicUrl.startsWith(publicUrlPrefix + "/")) {
            return;
        }

        String relativePath = publicUrl.substring((publicUrlPrefix + "/").length());
        Path target = uploadRoot.resolve(relativePath).normalize();

        if (!target.startsWith(uploadRoot)) {
            return;
        }

        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new IllegalStateException("이벤트 포스터 삭제에 실패했습니다.", e);
        }
    }

    /**
     * 업로드 파일의 확장자와 content-type이 이미지인지 검증한다.
     */
    private void validateImage(MultipartFile file, String extension) {
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("jpg, jpeg, png, webp의 확장자만 등록 가능합니다.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 등록 가능합니다.");
        }
    }

    /**
     * 원본 파일명에서 소문자 확장자를 추출한다.
     */
    private String extractExtension(String originalFilename) {
        String filename = StringUtils.cleanPath(originalFilename != null ? originalFilename : "");
        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("파일 확장자를 확인할 수 없습니다.");
        }

        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * URL prefix 비교가 흔들리지 않도록 마지막 슬래시를 제거한다.
     */
    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
