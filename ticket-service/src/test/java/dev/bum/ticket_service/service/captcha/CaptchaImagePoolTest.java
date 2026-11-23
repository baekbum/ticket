package dev.bum.ticket_service.service.captcha;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bum.ticket_service.config.CaptchaPoolProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class CaptchaImagePoolTest {

    @Test
    void generates_a_readable_png_and_six_character_answer() throws IOException {
        CaptchaImageGenerator.PreparedCaptcha captcha = new CaptchaImageGenerator().generate();

        assertThat(captcha.answer()).matches("[ACDEFGHJKMNPQRTUVWXY2346789]{6}");
        assertPng(captcha.png());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "TICKET_CAPTCHA_TEST_REDIS_PORT", matches = "\\d+")
    void scheduled_instances_refill_a_shared_pool_without_exceeding_capacity() throws Exception {
        int port = Integer.parseInt(System.getenv("TICKET_CAPTCHA_TEST_REDIS_PORT"));
        LettuceConnectionFactory connection = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration("127.0.0.1", port));
        connection.afterPropertiesSet();
        StringRedisTemplate redis = new StringRedisTemplate(connection);
        try {
            assertThat(redis.hasKey(CaptchaImagePool.KEY)).isFalse();
            ObjectMapper mapper = new ObjectMapper();
            ApplicationContextRunner runner = new ApplicationContextRunner()
                    .withUserConfiguration(ScheduledPoolConfig.class)
                    .withBean(StringRedisTemplate.class, () -> redis)
                    .withBean(ObjectMapper.class, () -> mapper)
                    .withPropertyValues("app.captcha.pool.capacity=7", "app.captcha.pool.batch-size=5",
                            "app.captcha.pool.refill-delay-ms=100");

            runner.run(first -> runner.run(second -> {
                assertThat(first).hasSingleBean(CaptchaImagePool.class);
                assertThat(second).hasSingleBean(CaptchaImagePool.class);
                await().atMost(Duration.ofSeconds(10)).during(Duration.ofMillis(300))
                        .untilAsserted(() -> assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(7));

                String entry = redis.opsForList().leftPop(CaptchaImagePool.KEY);
                assertThat(entry).isNotNull();
                JsonNode captcha = mapper.readTree(entry);
                assertThat(captcha.get("answer").asText()).matches("[ACDEFGHJKMNPQRTUVWXY2346789]{6}");
                assertPng(captcha.get("png").binaryValue());

                await().atMost(Duration.ofSeconds(10)).during(Duration.ofMillis(300))
                        .untilAsserted(() -> assertThat(redis.opsForList().size(CaptchaImagePool.KEY)).isEqualTo(7));
            }));
        } finally {
            connection.destroy();
        }
    }

    private static void assertPng(byte[] png) throws IOException {
        assertThat(png).startsWith(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(210);
        assertThat(image.getHeight()).isEqualTo(72);

        int darkPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xff) < 100) {
                    darkPixels++;
                }
            }
        }
        assertThat(darkPixels).isGreaterThan(200);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @EnableConfigurationProperties(CaptchaPoolProperties.class)
    @Import({CaptchaImagePool.class, CaptchaImageGenerator.class})
    static class ScheduledPoolConfig {
    }
}
