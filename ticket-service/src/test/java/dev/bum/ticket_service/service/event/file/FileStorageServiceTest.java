package dev.bum.ticket_service.service.event.file;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("이벤트 포스터 저장 시 public URL 반환")
    void save_event_poster() {
        FileStorageService fileStorageService = fileStorageService();
        MockMultipartFile file = imageFile("poster.png", "image/png");

        String publicUrl = fileStorageService.saveEventPoster(1L, file);

        assertThat(publicUrl).isEqualTo("/ticket/uploads/events/posters/26_08/poster.png");
        assertThat(tempDir.resolve(publicUrl.substring("/ticket/uploads/".length()))).exists();
    }

    @Test
    @DisplayName("같은 이름의 포스터가 있으면 숫자 suffix를 붙여 저장")
    void save_event_poster_with_duplicate_filename() {
        FileStorageService fileStorageService = fileStorageService();
        MockMultipartFile firstFile = imageFile("poster.png", "image/png");
        MockMultipartFile secondFile = imageFile("poster.png", "image/png");

        String firstPublicUrl = fileStorageService.saveEventPoster(1L, firstFile);
        String secondPublicUrl = fileStorageService.saveEventPoster(2L, secondFile);

        assertThat(firstPublicUrl).isEqualTo("/ticket/uploads/events/posters/26_08/poster.png");
        assertThat(secondPublicUrl).isEqualTo("/ticket/uploads/events/posters/26_08/poster_1.png");
        assertThat(tempDir.resolve("events/posters/26_08/poster.png")).exists();
        assertThat(tempDir.resolve("events/posters/26_08/poster_1.png")).exists();
    }

    @Test
    @DisplayName("파일이 없으면 null 반환")
    void save_event_poster_with_empty_file() {
        FileStorageService fileStorageService = fileStorageService();
        MockMultipartFile file = new MockMultipartFile("posterImage", "poster.png", "image/png", new byte[0]);

        String publicUrl = fileStorageService.saveEventPoster(1L, file);

        assertThat(publicUrl).isNull();
    }

    @Test
    @DisplayName("이벤트 ID가 없어도 월별 경로에 저장")
    void save_event_poster_without_event_id() {
        FileStorageService fileStorageService = fileStorageService();
        MockMultipartFile file = imageFile("poster.png", "image/png");

        String publicUrl = fileStorageService.saveEventPoster(null, file);

        assertThat(publicUrl).isEqualTo("/ticket/uploads/events/posters/26_08/poster.png");
    }

    @Test
    @DisplayName("허용되지 않는 확장자면 예외 발생")
    void save_event_poster_with_invalid_extension() {
        FileStorageService fileStorageService = fileStorageService();
        MockMultipartFile file = imageFile("poster.gif", "image/gif");

        assertThatThrownBy(() -> fileStorageService.saveEventPoster(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("이미지 content-type이 아니면 예외 발생")
    void save_event_poster_with_invalid_content_type() {
        FileStorageService fileStorageService = fileStorageService();
        MockMultipartFile file = new MockMultipartFile("posterImage", "poster.png", "text/plain", "image".getBytes());

        assertThatThrownBy(() -> fileStorageService.saveEventPoster(1L, file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("public URL로 저장된 파일 삭제")
    void delete_by_public_url() throws Exception {
        FileStorageService fileStorageService = fileStorageService();
        String publicUrl = fileStorageService.saveEventPoster(1L, imageFile("poster.png", "image/png"));
        Path storedFile = tempDir.resolve(publicUrl.substring("/ticket/uploads/".length()));

        fileStorageService.deleteByPublicUrl(publicUrl);

        assertThat(Files.exists(storedFile)).isFalse();
    }

    @Test
    @DisplayName("prefix가 다른 public URL은 삭제하지 않음")
    void delete_by_public_url_with_other_prefix() throws Exception {
        FileStorageService fileStorageService = fileStorageService();
        String publicUrl = fileStorageService.saveEventPoster(1L, imageFile("poster.png", "image/png"));
        Path storedFile = tempDir.resolve(publicUrl.substring("/ticket/uploads/".length()));

        fileStorageService.deleteByPublicUrl("/other/uploads/events/posters/1/poster.png");

        assertThat(Files.exists(storedFile)).isTrue();
    }

    private MockMultipartFile imageFile(String filename, String contentType) {
        return new MockMultipartFile("posterImage", filename, contentType, "image".getBytes());
    }

    private FileStorageService fileStorageService() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneId.of("Asia/Seoul"));
        return new FileStorageService(tempDir.toString(), "/ticket/uploads", fixedClock);
    }
}
