package dev.bum.ticket_service.service;

import dev.bum.ticket_service.service.event.file.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("이벤트 포스터 저장 시 public URL 반환")
    void save_event_poster() {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        MockMultipartFile file = imageFile("poster.png", "image/png");

        String publicUrl = fileStorageService.saveEventPoster(1L, file);

        assertThat(publicUrl).startsWith("/ticket/uploads/events/posters/1/");
        assertThat(publicUrl).endsWith(".png");
        assertThat(tempDir.resolve(publicUrl.substring("/ticket/uploads/".length()))).exists();
    }

    @Test
    @DisplayName("파일이 없으면 null 반환")
    void save_event_poster_with_empty_file() {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        MockMultipartFile file = new MockMultipartFile("posterImage", "poster.png", "image/png", new byte[0]);

        String publicUrl = fileStorageService.saveEventPoster(1L, file);

        assertThat(publicUrl).isNull();
    }

    @Test
    @DisplayName("이벤트 ID가 없으면 예외 발생")
    void save_event_poster_without_event_id() {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        MockMultipartFile file = imageFile("poster.png", "image/png");

        assertThatThrownBy(() -> fileStorageService.saveEventPoster(null, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("허용되지 않는 확장자면 예외 발생")
    void save_event_poster_with_invalid_extension() {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        MockMultipartFile file = imageFile("poster.gif", "image/gif");

        assertThatThrownBy(() -> fileStorageService.saveEventPoster(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이미지 content-type이 아니면 예외 발생")
    void save_event_poster_with_invalid_content_type() {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        MockMultipartFile file = new MockMultipartFile("posterImage", "poster.png", "text/plain", "image".getBytes());

        assertThatThrownBy(() -> fileStorageService.saveEventPoster(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("public URL로 저장된 파일 삭제")
    void delete_by_public_url() throws Exception {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        String publicUrl = fileStorageService.saveEventPoster(1L, imageFile("poster.png", "image/png"));
        Path storedFile = tempDir.resolve(publicUrl.substring("/ticket/uploads/".length()));

        fileStorageService.deleteByPublicUrl(publicUrl);

        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    @DisplayName("prefix가 다른 public URL은 삭제하지 않음")
    void delete_by_public_url_with_other_prefix() throws Exception {
        FileStorageService fileStorageService = new FileStorageService(tempDir.toString(), "/ticket/uploads");
        String publicUrl = fileStorageService.saveEventPoster(1L, imageFile("poster.png", "image/png"));
        Path storedFile = tempDir.resolve(publicUrl.substring("/ticket/uploads/".length()));

        fileStorageService.deleteByPublicUrl("/other/uploads/events/posters/1/poster.png");

        assertThat(Files.exists(storedFile)).isTrue();
    }

    private MockMultipartFile imageFile(String filename, String contentType) {
        return new MockMultipartFile("posterImage", filename, contentType, "image".getBytes());
    }
}
