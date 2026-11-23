package dev.bum.ticket_service.controller.captcha;

import dev.bum.ticket_service.service.captcha.CaptchaChallengeResponse;
import dev.bum.ticket_service.service.captcha.CaptchaChallengeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return captchaChallengeService.issue(eventId, currentUserId, activeToken)
                .map(challenge -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(challenge))
                .orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
    }
}
