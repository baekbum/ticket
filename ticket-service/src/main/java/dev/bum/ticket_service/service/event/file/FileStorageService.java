package dev.bum.ticket_service.service.event.file;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final DateTimeFormatter POSTER_DIR_FORMATTER = DateTimeFormatter.ofPattern("yy_MM");

    private final Path uploadRoot;
    private final String publicUrlPrefix;
    private final Clock clock;

    @Autowired
    public FileStorageService(
            @Value("${app.upload.root:uploads}") String uploadRoot,
            @Value("${app.upload.public-url-prefix:/ticket/uploads}") String publicUrlPrefix
    ) {
        this(uploadRoot, publicUrlPrefix, Clock.systemDefaultZone());
    }

    FileStorageService(String uploadRoot, String publicUrlPrefix, Clock clock) {
        this.uploadRoot = Paths.get(uploadRoot).toAbsolutePath().normalize();
        this.publicUrlPrefix = trimTrailingSlash(publicUrlPrefix);
        this.clock = clock;
    }

    /**
     * 이벤트 포스터 이미지를 검증한 뒤 월별 업로드 경로에 저장하고 공개 URL을 반환한다.
     */
    public String saveEventPoster(Long eventId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = file.getOriginalFilename();
        String extension = extractExtension(originalFilename);
        validateImage(file, extension);

        String storedFileName;
        String posterDirName = LocalDate.now(clock).format(POSTER_DIR_FORMATTER);
        Path posterDir = uploadRoot.resolve("events").resolve("posters").resolve(posterDirName).normalize();

        try {
            Files.createDirectories(posterDir);
            Path target = copyWithUniqueName(file, posterDir, originalFilename, extension);
            storedFileName = target.getFileName().toString();
        } catch (IOException e) {
            throw new IllegalStateException("이벤트 포스터 저장에 실패했습니다.", e);
        }

        return publicUrlPrefix + "/events/posters/" + posterDirName + "/" + storedFileName;
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

    private Path copyWithUniqueName(MultipartFile file, Path posterDir, String originalFilename, String extension) throws IOException {
        for (int sequence = 0; ; sequence++) {
            String storedFileName = buildStoredFileName(originalFilename, extension, sequence);
            Path target = posterDir.resolve(storedFileName).normalize();

            if (!target.startsWith(posterDir)) {
                throw new IllegalArgumentException("유효하지 않은 파일 경로입니다.");
            }

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, target);
                return target;
            } catch (FileAlreadyExistsException e) {
                continue;
            }
        }
    }

    private String buildStoredFileName(String originalFilename, String extension, int sequence) {
        String filename = StringUtils.cleanPath(originalFilename != null ? originalFilename : "");
        filename = filename.replace("\\", "/");
        int slashIndex = filename.lastIndexOf('/');
        if (slashIndex >= 0) {
            filename = filename.substring(slashIndex + 1);
        }

        int dotIndex = filename.lastIndexOf('.');
        String basename = dotIndex > 0 ? filename.substring(0, dotIndex) : "poster";
        basename = basename.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").strip();
        if (!StringUtils.hasText(basename)) {
            basename = "poster";
        }

        String suffix = sequence == 0 ? "" : "_" + sequence;
        return basename + suffix + "." + extension;
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
