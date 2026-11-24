package dev.bum.ticket_service.controller;

import dev.bum.common.jwt.JwtTokenProvider;
import dev.bum.common.security.TokenState;
import dev.bum.common.security.TokenStateStore;
import dev.bum.ticket_service.controller.captcha.CaptchaController;
import dev.bum.ticket_service.exception.captcha.CaptchaChallengeChangedException;
import dev.bum.ticket_service.exception.captcha.CaptchaChallengeExpiredException;
import dev.bum.ticket_service.exception.captcha.CaptchaRefreshRateLimitedException;
import dev.bum.ticket_service.exception.captcha.CaptchaUnavailableException;
import dev.bum.ticket_service.security.InternalServiceTokenValidator;
import dev.bum.ticket_service.security.SecurityConfig;
import dev.bum.ticket_service.service.captcha.CaptchaChallengeResponse;
import dev.bum.ticket_service.service.captcha.CaptchaChallengeService;
import dev.bum.ticket_service.service.captcha.CaptchaVerificationResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptchaController.class)
@Import(SecurityConfig.class)
class CaptchaControllerTest {

    private static final String URL = "/api/v1/captcha/events/1/challenge";

    @Autowired private MockMvc mvc;
    @MockitoBean private CaptchaChallengeService challenges;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private TokenStateStore tokenStateStore;
    @MockitoBean private InternalServiceTokenValidator internalServiceTokenValidator;

    @Test
    void requires_login() throws Exception {
        mvc.perform(post(URL).header("X-Active-Token", "active-token"))
                .andExpect(status().is4xxClientError());
        verifyNoInteractions(challenges);
    }

    @Test
    void accepts_a_user_bearer_token_and_exposes_only_the_challenge_image_and_expiry() throws Exception {
        stubBearerToken();
        UUID id = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-12-01T10:02:00Z");
        given(challenges.issue(1L, "user01", "active-token"))
                .willReturn(Optional.of(new CaptchaChallengeResponse(id, "png-base64", expiresAt)));

        mvc.perform(post(URL).header("Authorization", "Bearer access-token").header("X-Active-Token", "active-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.challengeId").value(id.toString()))
                .andExpect(jsonPath("$.imageBase64").value("png-base64"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test
    void distinguishes_events_without_captcha_from_a_temporarily_empty_pool() throws Exception {
        stubBearerToken();
        given(challenges.issue(1L, "user01", "active-token"))
                .willReturn(Optional.empty())
                .willThrow(new CaptchaUnavailableException());

        mvc.perform(post(URL).header("Authorization", "Bearer access-token").header("X-Active-Token", "active-token"))
                .andExpect(status().isNoContent());
        mvc.perform(post(URL).header("Authorization", "Bearer access-token").header("X-Active-Token", "active-token"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CAPTCHA_UNAVAILABLE"));
    }

    @Test
    void verify_requires_the_current_challenge_id_and_answer() throws Exception {
        stubBearerToken();
        mvc.perform(post(URL + "/verify").header("Authorization", "Bearer access-token")
                        .header("X-Active-Token", "active-token").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(challenges);
    }

    @Test
    void refresh_returns_only_the_replacement_and_distinguishes_expiration_from_rate_limiting() throws Exception {
        stubBearerToken();
        UUID replacement = UUID.randomUUID();
        Instant expiresAt = Instant.parse("2026-12-01T10:02:00Z");
        given(challenges.refresh(1L, "user01", "active-token"))
                .willReturn(Optional.of(new CaptchaChallengeResponse(replacement, "replacement-png", expiresAt)))
                .willThrow(new CaptchaChallengeExpiredException())
                .willThrow(new CaptchaRefreshRateLimitedException());
        MockHttpServletRequestBuilder request = post(URL + "/refresh").header("Authorization", "Bearer access-token")
                .header("X-Active-Token", "active-token");

        mvc.perform(request).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.challengeId").value(replacement.toString()))
                .andExpect(jsonPath("$.imageBase64").value("replacement-png"))
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andExpect(jsonPath("$.answer").doesNotExist());
        mvc.perform(request).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("CAPTCHA_CHALLENGE_EXPIRED"));
        mvc.perform(request).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("CAPTCHA_REFRESH_RATE_LIMITED"));
    }

    @Test
    void verify_forwards_the_challenge_id_and_answer_and_distinguishes_an_older_challenge() throws Exception {
        stubBearerToken();
        UUID id = UUID.randomUUID();
        given(challenges.verify(1L, "user01", "active-token", id, "A2K7MC"))
                .willReturn(new CaptchaVerificationResponse(false))
                .willReturn(new CaptchaVerificationResponse(true))
                .willThrow(new CaptchaChallengeChangedException());

        MockHttpServletRequestBuilder request = post(URL + "/verify").header("Authorization", "Bearer access-token")
                .header("X-Active-Token", "active-token").contentType(MediaType.APPLICATION_JSON)
                .content("{\"challengeId\":\"" + id + "\",\"answer\":\"A2K7MC\"}");

        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.passed").value(false));
        mvc.perform(request).andExpect(status().isOk()).andExpect(jsonPath("$.passed").value(true));
        mvc.perform(request).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPTCHA_CHALLENGE_CHANGED"));
    }

    private void stubBearerToken() {
        given(jwtTokenProvider.validateToken("access-token")).willReturn(true);
        given(jwtTokenProvider.getTokenType("access-token")).willReturn("access");
        given(jwtTokenProvider.getUserId("access-token")).willReturn("user01");
        given(jwtTokenProvider.getTokenVersion("access-token")).willReturn(1L);
        given(jwtTokenProvider.getRole("access-token")).willReturn("ROLE_USER");
        given(tokenStateStore.get("user01")).willReturn(new TokenState(1L, true, "ROLE_USER"));
        given(jwtTokenProvider.getAuthentication("access-token")).willReturn(new UsernamePasswordAuthenticationToken(
                "user01", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }
}
