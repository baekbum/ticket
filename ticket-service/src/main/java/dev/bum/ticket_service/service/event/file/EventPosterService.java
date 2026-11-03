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

    // 파일 저장 업무 처리를 담당하는 서비스.
    private final FileStorageService fileStorageService;

    /**
     * 트랜잭션 결과에 맞춰 업로드 파일의 저장과 정리를 관리한다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String saveForTransaction(MultipartFile posterImage, String previousPosterUrl) {

        String newPosterUrl = fileStorageService.saveEventPoster(posterImage);
        if (newPosterUrl == null) return null;

        // 저장 직후 등록하여 이후 검증·다회차 등록·DB 커밋 실패에도 새 파일을 정리한다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

            /**
             * 트랜잭션 커밋이 완료된 뒤 등록한 후속 작업을 실행한다.
             */
            @Override
            public void afterCommit() {

                // DB가 새 포스터를 가리키는 것이 확정된 뒤 기존 파일을 삭제한다.
                if (!Objects.equals(previousPosterUrl, newPosterUrl)) {
                    deleteSafely(previousPosterUrl, "COMMITTED");
                }
            }

            /**
             * 처리 정보 처리에서 트랜잭션 종료 결과에 따른 후속 처리를 수행한다.
             */
            @Override
            public void afterCompletion(int status) {

                // 결과를 알 수 없는 경우에는 커밋됐을 가능성이 있으므로 새 파일을 보존한다.
                if (status == STATUS_ROLLED_BACK) deleteSafely(newPosterUrl, "ROLLED_BACK");
            }
        });
        return newPosterUrl;
    }

    /**
     * 파일 삭제 실패를 처리하면서 저장 파일을 정리한다.
     */
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
