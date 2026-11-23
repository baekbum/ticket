package dev.bum.ticket_service.service.captcha;

import java.time.Instant;
import java.util.UUID;

public record CaptchaChallengeResponse(UUID challengeId, String imageBase64, Instant expiresAt) {
}
