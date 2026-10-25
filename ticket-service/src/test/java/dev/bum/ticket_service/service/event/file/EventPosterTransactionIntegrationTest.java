package dev.bum.ticket_service.service.event.file;

import com.querydsl.jpa.impl.JPAQueryFactory;
import dev.bum.common.service.ticket.event.event.dto.InsertEventBulkRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventCommonRequest;
import dev.bum.common.service.ticket.event.event.dto.InsertEventScheduleRequest;
import dev.bum.common.service.ticket.event.event.dto.UpdateEventRequest;
import dev.bum.common.service.ticket.event.event.enums.EventStatus;
import dev.bum.ticket_service.jpa.event.event.Event;
import dev.bum.ticket_service.jpa.event.event.EventJpaRepository;
import dev.bum.ticket_service.jpa.event.event.EventRepositoryImpl;
import dev.bum.ticket_service.service.event.event.EventManagementService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;

@DataJpaTest(showSql = false)
@ContextConfiguration(classes = EventPosterTransactionIntegrationTest.PersistenceConfig.class)
@Import({EventManagementService.class, EventPosterService.class, FileStorageService.class, EventRepositoryImpl.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EventPosterTransactionIntegrationTest {
    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackages = "dev.bum.ticket_service.jpa")
    @EnableJpaRepositories(basePackages = "dev.bum.ticket_service.jpa")
    static class PersistenceConfig {
        @Bean
        JPAQueryFactory queryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @TempDir
    static Path uploadRoot;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.upload.root", () -> uploadRoot.toString());
        registry.add("app.upload.public-url-prefix", () -> "/ticket/uploads");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired EventManagementService service;
    @Autowired EventPosterService posters;
    @MockitoSpyBean FileStorageService files;
    @Autowired EventJpaRepository events;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void cleanDatabase() {
        events.deleteAll();
    }

    @Test
    void oldPosterIsDeletedOnlyAfterDatabaseCommit() {
        Event original = fixture();
        String newUrl = new TransactionTemplate(transactions).execute(tx -> {
            String url = service.update(original.getEventId(), update("수정 공연"), image()).getPosterUrl();
            assertThat(path(original.getPosterUrl())).exists();
            assertThat(path(url)).exists();
            return url;
        });

        assertThat(events.findById(original.getEventId()).orElseThrow().getPosterUrl()).isEqualTo(newUrl);
        assertThat(path(original.getPosterUrl())).doesNotExist();
        assertThat(path(newUrl)).exists();
    }

    @Test
    void outerRollbackPreservesOldPosterAndDeletesNewPoster() {
        Event original = fixture();
        String[] newUrl = new String[1];

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            newUrl[0] = service.update(original.getEventId(), update("수정 공연"), image()).getPosterUrl();
            throw new IllegalStateException("후속 작업 실패");
        })).isInstanceOf(IllegalStateException.class).hasMessage("후속 작업 실패");

        assertThat(events.findById(original.getEventId()).orElseThrow().getPosterUrl())
                .isEqualTo(original.getPosterUrl());
        assertThat(path(original.getPosterUrl())).exists();
        assertThat(path(newUrl[0])).doesNotExist();
    }

    @Test
    void databaseCommitFailureAlsoDeletesNewPoster() throws IOException {
        Event original = fixture();
        long before = fileCount();

        // 엔티티 수정은 통과하되 커밋 시 DB의 제목 길이 제약으로 실패한다.
        assertThatThrownBy(() -> service.update(original.getEventId(), update("x".repeat(101)), image()))
                .isInstanceOf(RuntimeException.class);

        Event persisted = events.findById(original.getEventId()).orElseThrow();
        assertThat(persisted.getTitle()).isEqualTo("기존 공연");
        assertThat(persisted.getPosterUrl()).isEqualTo(original.getPosterUrl());
        assertThat(path(original.getPosterUrl())).exists();
        assertThat(fileCount()).isEqualTo(before);
    }

    @Test
    void laterScheduleFailureCleansEarlierUploadedPosters() throws IOException {
        long before = fileCount();
        InsertEventBulkRequest request = bulkRequest();
        request.setSchedules(Arrays.asList(request.getSchedules().getFirst(), null));

        assertThatThrownBy(() -> service.insertBulk(request, image())).isInstanceOf(RuntimeException.class);

        assertThat(events.count()).isZero();
        assertThat(fileCount()).isEqualTo(before);
    }

    @Test
    void successfulBulkInsertKeepsEveryNewPoster() {
        var responses = service.insertBulk(bulkRequest(), image());

        assertThat(events.count()).isEqualTo(2);
        assertThat(responses).hasSize(2).allSatisfy(response -> assertThat(path(response.getPosterUrl())).exists());
        assertThat(responses.get(0).getPosterUrl()).isNotEqualTo(responses.get(1).getPosterUrl());
    }

    @Test
    void updateWithoutUploadKeepsExistingPoster() {
        Event original = fixture();

        var response = service.update(original.getEventId(), update("수정 공연"), null);

        assertThat(response.getPosterUrl()).isEqualTo(original.getPosterUrl());
        assertThat(path(original.getPosterUrl())).exists();
    }

    @Test
    void oldFileDeletionFailureDoesNotTurnCommittedUpdateIntoFailure() {
        Event original = fixture();
        doThrow(new IllegalStateException("삭제 실패")).when(files).deleteByPublicUrl(original.getPosterUrl());

        var response = service.update(original.getEventId(), update("수정 공연"), image());

        assertThat(events.findById(original.getEventId()).orElseThrow().getPosterUrl())
                .isEqualTo(response.getPosterUrl());
        assertThat(path(response.getPosterUrl())).exists();
        assertThat(path(original.getPosterUrl())).exists();
    }

    @Test
    void rollbackCleanupFailureDoesNotHideOriginalBusinessFailure() {
        Event original = fixture();
        doThrow(new IllegalStateException("삭제 실패")).when(files).deleteByPublicUrl(anyString());

        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.update(original.getEventId(), update("수정 공연"), image());
            throw new IllegalArgumentException("원래 작업 실패");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("원래 작업 실패");

        assertThat(events.findById(original.getEventId()).orElseThrow().getPosterUrl())
                .isEqualTo(original.getPosterUrl());
        assertThat(path(original.getPosterUrl())).exists();
    }

    @Test
    void uploadRequiresAnExistingTransaction() throws IOException {
        long before = fileCount();

        assertThatThrownBy(() -> posters.saveForTransaction(image(), null))
                .isInstanceOf(IllegalTransactionStateException.class);

        assertThat(fileCount()).isEqualTo(before);
    }

    private Event fixture() {
        String url = files.saveEventPoster(image());
        return events.saveAndFlush(Event.builder()
                .artistName("IU").title("기존 공연").venue("공연장").posterUrl(url)
                .eventDateTime(LocalDateTime.of(2027, 1, 1, 18, 0))
                .totalSeats(10).availableSeats(10).status(EventStatus.ON_SALE).maxTicketsPerPerson(4)
                .build());
    }

    private UpdateEventRequest update(String title) {
        return UpdateEventRequest.builder().title(title).build();
    }

    private InsertEventBulkRequest bulkRequest() {
        LocalDateTime first = LocalDateTime.of(2027, 1, 1, 18, 0);
        return InsertEventBulkRequest.builder()
                .common(InsertEventCommonRequest.builder()
                        .artistName("IU").title("등록 공연").venue("공연장")
                        .totalSeats(10).maxTicketsPerPerson(4).build())
                .schedules(List.of(
                        InsertEventScheduleRequest.builder().eventDateTime(first).build(),
                        InsertEventScheduleRequest.builder().eventDateTime(first.plusDays(1)).build()
                )).build();
    }

    private MockMultipartFile image() {
        return new MockMultipartFile("posterImage", "poster.png", "image/png", "image".getBytes());
    }

    private Path path(String url) {
        return uploadRoot.resolve(url.substring("/ticket/uploads/".length()));
    }

    private long fileCount() throws IOException {
        try (var paths = Files.walk(uploadRoot)) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
}
