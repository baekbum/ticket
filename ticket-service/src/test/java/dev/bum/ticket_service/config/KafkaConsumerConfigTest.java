package dev.bum.ticket_service.config;

import dev.bum.common.kafka.dlt.KafkaDltSlackNotifier;
import dev.bum.common.kafka.payment.VirtualAccountExpiredEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.apache.kafka.common.header.internals.RecordHeaders;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KafkaConsumerConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
            .withUserConfiguration(KafkaConsumerConfig.class)
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092",
                    "topic.payment.virtual-account.expired.name=virtual-account-expired",
                    "spring.kafka.listener.observation-enabled=true",
                    "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
                    "spring.kafka.consumer.properties.spring.json.trusted.packages=dev.bum.*")
            .withBean(KafkaDltSlackNotifier.class, () -> mock(KafkaDltSlackNotifier.class));

    @Test
    @DisplayName("Kafka DLT error handler bean을 생성")
    void kafkaErrorHandlerBeanCreated() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(DefaultErrorHandler.class)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void consumesHeaderlessGatewayEventWithSharedFactoryAndErrorHandler() {
        contextRunner.run(context -> {
            var listener = org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation(
                    org.springframework.util.ReflectionUtils.findMethod(
                            dev.bum.ticket_service.kafka.payment.VirtualAccountExpiredConsumer.class,
                            "consume", VirtualAccountExpiredEvent.class),
                    org.springframework.kafka.annotation.KafkaListener.class);
            assertThat(listener.containerFactory()).isEqualTo("eventKafkaListenerContainerFactory");
            var factory = (ConcurrentKafkaListenerContainerFactory<String, Object>)
                    context.getBean("eventKafkaListenerContainerFactory");
            var consumerFactory = (DefaultKafkaConsumerFactory<String, Object>)
                    factory.getConsumerFactory();
            var container = factory.createContainer("virtual-account-expired");
            assertThat(container.getCommonErrorHandler()).isSameAs(context.getBean(DefaultErrorHandler.class));
            assertThat(container.getContainerProperties().isObservationEnabled()).isTrue();
            var deserializer = consumerFactory.getValueDeserializer();
            assertThat(deserializer).isInstanceOf(ErrorHandlingDeserializer.class);
            deserializer.configure(consumerFactory.getConfigurationProperties(), false);

            var event = VirtualAccountExpiredEvent.builder().paymentNo("PAY-test")
                    .amount(new BigDecimal("180000"))
                    .expiredAt(LocalDateTime.of(2026, 10, 7, 0, 0)).build();
            var headers = new RecordHeaders();
            byte[] payload = new JsonSerializer<VirtualAccountExpiredEvent>().noTypeInfo()
                    .serialize("virtual-account-expired", headers, event);
            assertThat(headers.lastHeader("__TypeId__")).isNull();
            assertThat(deserializer
                    .deserialize("virtual-account-expired", headers, payload)).isEqualTo(event);

            // 오래되거나 관련 없는 Java 타입 헤더가 있어도 토픽에 지정한 이벤트 타입을 유지해야 한다.
            headers.add("__TypeId__", "java.lang.String".getBytes(StandardCharsets.UTF_8));
            assertThat(deserializer
                    .deserialize("virtual-account-expired", headers, payload)).isEqualTo(event);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedJsonBecomesRecordErrorWithOriginalBytesForDlt() {
        contextRunner.run(context -> {
            var factory = (ConcurrentKafkaListenerContainerFactory<String, Object>)
                    context.getBean("eventKafkaListenerContainerFactory");
            var consumerFactory = (DefaultKafkaConsumerFactory<String, Object>)
                    factory.getConsumerFactory();
            var headers = new RecordHeaders();
            byte[] payload = "{not-json".getBytes(StandardCharsets.UTF_8);
            var deserializer = consumerFactory.getValueDeserializer();
            deserializer.configure(consumerFactory.getConfigurationProperties(), false);
            assertThat(deserializer
                    .deserialize("virtual-account-expired", headers, payload)).isNull();
            assertThat(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNotNull();
            var record = new org.apache.kafka.clients.consumer.ConsumerRecord<String, Object>(
                    "virtual-account-expired", 1, 0, "PAY-test", null);
            headers.forEach(record.headers()::add);
            var exception = SerializationUtils.getExceptionFromHeader(record,
                    SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER,
                    new org.springframework.core.log.LogAccessor(KafkaConsumerConfigTest.class));
            assertThat(exception).isNotNull();
            assertThat(exception.getData()).isEqualTo(payload);
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolvesConfiguredTopicNameInsteadOfHardcodedName() {
        contextRunner.withPropertyValues("topic.payment.virtual-account.expired.name=custom-expired")
                .run(context -> {
                    var factory = (ConcurrentKafkaListenerContainerFactory<String, Object>)
                            context.getBean("eventKafkaListenerContainerFactory");
                    var deserializer = factory.getConsumerFactory().getValueDeserializer();
                    deserializer.configure(factory.getConsumerFactory().getConfigurationProperties(), false);
                    var payload = "{\"paymentNo\":\"PAY-custom\"}".getBytes(StandardCharsets.UTF_8);
                    assertThat(deserializer.deserialize("custom-expired", new RecordHeaders(), payload))
                            .isEqualTo(VirtualAccountExpiredEvent.builder().paymentNo("PAY-custom").build());
                    var headers = new RecordHeaders();
                    assertThat(deserializer.deserialize("virtual-account-expired", headers, payload)).isNull();
                    assertThat(headers.lastHeader(SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER)).isNotNull();
                });
    }
}
