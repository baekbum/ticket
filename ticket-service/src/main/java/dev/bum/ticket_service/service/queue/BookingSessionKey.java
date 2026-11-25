package dev.bum.ticket_service.service.queue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class BookingSessionKey {

    private BookingSessionKey() {
    }

    public static String hash(Long eventId, String userId, String activeToken) {
        String binding = eventId + "\0" + userId + "\0" + activeToken;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(binding.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
