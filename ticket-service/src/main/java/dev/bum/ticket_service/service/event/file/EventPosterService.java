package dev.bum.ticket_service.service.event.file;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventPosterService {
    private final FileStorageService fileStorageService;

    @Transactional(propagation = Propagation.MANDATORY)
    public String saveForTransaction(MultipartFile posterImage, String previousPosterUrl) {
        String newPosterUrl = fileStorageService.saveEventPoster(posterImage);
        if (newPosterUrl == null) return null;

        // 저장 직후 등록하여 이후 검증·다회차 등록·DB 커밋 실패에도 새 파일을 정리한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // DB가 새 포스터를 가리키는 것이 확정된 뒤 기존 파일을 삭제한다.
                if (!Objects.equals(previousPosterUrl, newPosterUrl)) {
                    deleteSafely(previousPosterUrl, "COMMITTED");
                }
            }

            @Override
            public void afterCompletion(int status) {
                // 결과를 알 수 없는 경우에는 커밋됐을 가능성이 있으므로 새 파일을 보존한다.
                if (status == STATUS_ROLLED_BACK) deleteSafely(newPosterUrl, "ROLLED_BACK");
            }
        });
        return newPosterUrl;
    }

    private void deleteSafely(String publicUrl, String completion) {
        if (publicUrl == null) return;

        try {
            fileStorageService.deleteByPublicUrl(publicUrl);
        } catch (RuntimeException e) {
            // 파일 정리 실패가 이미 확정된 DB 결과나 원래 실패 원인을 바꾸지 않도록 한다.
            log.warn("포스터 파일 정리 실패: completion={}, publicUrl={}, errorType={}",
                    completion, publicUrl, e.getClass().getSimpleName());
        }
    }
}
