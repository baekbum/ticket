package dev.bum.ticket_service.controller.captcha;

import dev.bum.ticket_service.service.captcha.CaptchaChallengeResponse;
import dev.bum.ticket_service.service.captcha.CaptchaChallengeService;
import dev.bum.ticket_service.service.captcha.CaptchaVerificationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/captcha/events/{eventId}")
@RequiredArgsConstructor
public class CaptchaController {

    private final CaptchaChallengeService captchaChallengeService;

    @PostMapping("/challenge")
    public ResponseEntity<CaptchaChallengeResponse> issue(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        return challengeResponse(captchaChallengeService.issue(eventId, currentUserId, activeToken));
    }

    @PostMapping("/challenge/refresh")
    public ResponseEntity<CaptchaChallengeResponse> refresh(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken
    ) {
        return challengeResponse(captchaChallengeService.refresh(eventId, currentUserId, activeToken));
    }

    @PostMapping("/challenge/verify")
    public ResponseEntity<CaptchaVerificationResponse> verify(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader(value = "X-Active-Token", required = false) String activeToken,
            @Valid @RequestBody VerifyRequest request
    ) {
        CaptchaVerificationResponse result = captchaChallengeService.verify(
                eventId, currentUserId, activeToken, request.challengeId(), request.answer());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/session/close")
    public ResponseEntity<Void> closeSession(
            @PathVariable("eventId") Long eventId,
            @AuthenticationPrincipal String currentUserId,
            @RequestHeader("X-Active-Token") String activeToken
    ) {
        captchaChallengeService.closeSession(eventId, currentUserId, activeToken);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<CaptchaChallengeResponse> challengeResponse(Optional<CaptchaChallengeResponse> response) {
        return response
                .map(challenge -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(challenge))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }

    public record VerifyRequest(@NotNull UUID challengeId, @NotBlank @Size(max = 64) String answer) {
    }
}
