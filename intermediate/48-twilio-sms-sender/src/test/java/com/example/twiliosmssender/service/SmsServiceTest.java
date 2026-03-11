package com.example.twiliosmssender.service;

import com.example.twiliosmssender.domain.SmsMessage;
import com.example.twiliosmssender.domain.SmsStatus;
import com.example.twiliosmssender.dto.SendSmsRequest;
import com.example.twiliosmssender.dto.SmsResponse;
import com.example.twiliosmssender.exception.SmsNotFoundException;
import com.example.twiliosmssender.exception.TwilioSmsException;
import com.example.twiliosmssender.repository.SmsMessageRepository;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SmsService}.
 *
 * <h2>Testing strategy</h2>
 * <p>The Twilio SDK uses static factory methods ({@code Message.creator(...).create()}).
 * To avoid real Twilio HTTP calls in unit tests, we spy on the {@link SmsService}
 * and stub the package-private {@code createTwilioMessage(...)} method — which wraps
 * the Twilio SDK call — to return a mock {@link Message} object.
 *
 * <p>This pattern lets us test all service logic (status mapping, repository interactions,
 * error handling) without any network I/O or Twilio credentials.
 *
 * <h2>Key patterns demonstrated</h2>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} – lightweight Mockito injection.</li>
 *   <li>{@code @Spy} + {@code doReturn()} – spy on the real service to stub only the
 *       Twilio SDK call while exercising all other real logic.</li>
 *   <li>{@code @Mock} – replace the repository with a mock (no real database).</li>
 *   <li>{@code @Nested} – group related tests by method under test.</li>
 *   <li>{@code ReflectionTestUtils.setField()} – inject {@code @Value} fields in tests
 *       since there is no Spring context.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SmsService unit tests")
class SmsServiceTest {

    /** Mocked repository – no real database connection. */
    @Mock
    private SmsMessageRepository smsMessageRepository;

    /**
     * A real {@link SmsService} instance wrapped in a Mockito spy.
     *
     * <p>Important: we cannot use {@code @Spy SmsService = new SmsService(smsMessageRepository)}
     * at field-declaration time because {@code smsMessageRepository} is still null when the field
     * initializer runs (Mockito injects mocks before {@code @BeforeEach} but after field init).
     * Instead, we construct the service manually inside {@code setUp()} after the mock is ready,
     * then wrap it in a spy so we can stub only {@code createTwilioMessage()} while keeping all
     * other method implementations real.
     */
    private SmsService smsService;

    @BeforeEach
    void setUp() {
        // Construct the real service with the already-injected mock repository.
        // Both the spy and the internal implementation will share the same mock instance.
        SmsService realService = new SmsService(smsMessageRepository);

        // Wrap with spy so individual methods can be stubbed (e.g., createTwilioMessage)
        smsService = org.mockito.Mockito.spy(realService);

        // Inject the @Value field 'fromPhoneNumber' since there is no Spring context.
        // ReflectionTestUtils is a Spring test utility for setting private/package fields.
        ReflectionTestUtils.setField(smsService, "fromPhoneNumber", "+15559876543");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // mapTwilioStatus (pure logic – no mocks needed)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("mapTwilioStatus")
    class MapTwilioStatusTests {

        @Test
        @DisplayName("should return QUEUED for 'queued'")
        void mapTwilioStatus_queued() {
            assertThat(smsService.mapTwilioStatus("queued")).isEqualTo(SmsStatus.QUEUED);
        }

        @Test
        @DisplayName("should return SENDING for 'sending'")
        void mapTwilioStatus_sending() {
            assertThat(smsService.mapTwilioStatus("sending")).isEqualTo(SmsStatus.SENDING);
        }

        @Test
        @DisplayName("should return SENT for 'sent'")
        void mapTwilioStatus_sent() {
            assertThat(smsService.mapTwilioStatus("sent")).isEqualTo(SmsStatus.SENT);
        }

        @Test
        @DisplayName("should return DELIVERED for 'delivered'")
        void mapTwilioStatus_delivered() {
            assertThat(smsService.mapTwilioStatus("delivered")).isEqualTo(SmsStatus.DELIVERED);
        }

        @Test
        @DisplayName("should return FAILED for 'failed'")
        void mapTwilioStatus_failed() {
            assertThat(smsService.mapTwilioStatus("failed")).isEqualTo(SmsStatus.FAILED);
        }

        @Test
        @DisplayName("should return UNDELIVERED for 'undelivered'")
        void mapTwilioStatus_undelivered() {
            assertThat(smsService.mapTwilioStatus("undelivered")).isEqualTo(SmsStatus.UNDELIVERED);
        }

        @Test
        @DisplayName("should return QUEUED for any unknown status string")
        void mapTwilioStatus_unknown() {
            // Unknown/future Twilio statuses should safely default to QUEUED
            assertThat(smsService.mapTwilioStatus("some_future_status")).isEqualTo(SmsStatus.QUEUED);
        }

        @Test
        @DisplayName("should return QUEUED for null input")
        void mapTwilioStatus_null() {
            assertThat(smsService.mapTwilioStatus(null)).isEqualTo(SmsStatus.QUEUED);
        }

        @Test
        @DisplayName("should be case-insensitive")
        void mapTwilioStatus_caseInsensitive() {
            // Twilio status strings are lowercase, but defensive handling is good practice
            assertThat(smsService.mapTwilioStatus("DELIVERED")).isEqualTo(SmsStatus.DELIVERED);
            assertThat(smsService.mapTwilioStatus("Sent")).isEqualTo(SmsStatus.SENT);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // sendSms
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("sendSms")
    class SendSmsTests {

        @Test
        @DisplayName("should send SMS and return QUEUED response on success")
        void sendSms_success() {
            // Arrange – prepare a mock Twilio Message to return from the stubbed SDK call
            Message mockTwilioMessage = mock(Message.class);
            when(mockTwilioMessage.getSid()).thenReturn("SM1234567890abcdef1234567890abcdef");
            when(mockTwilioMessage.getStatus()).thenReturn(Message.Status.QUEUED);

            // Stub the Twilio SDK call so no real HTTP request is made
            doReturn(mockTwilioMessage).when(smsService)
                    .createTwilioMessage(any(PhoneNumber.class), any(PhoneNumber.class), anyString());

            // Stub repository save to return a persisted entity with id=1
            SmsMessage savedEntity = buildSmsMessage(1L, "+15551234567", "+15559876543",
                    "Hello!", SmsStatus.QUEUED);
            when(smsMessageRepository.save(any(SmsMessage.class))).thenReturn(savedEntity);

            // Act
            SmsResponse response = smsService.sendSms(new SendSmsRequest("+15551234567", "Hello!"));

            // Assert – the response reflects the persisted entity
            assertThat(response.id()).isEqualTo(1L);
            assertThat(response.toNumber()).isEqualTo("+15551234567");
            assertThat(response.body()).isEqualTo("Hello!");
            assertThat(response.status()).isEqualTo(SmsStatus.QUEUED);

            // Verify the repository was called twice: once for initial save, once for status update
            verify(smsMessageRepository, times(2)).save(any(SmsMessage.class));
        }

        @Test
        @DisplayName("should throw TwilioSmsException and save FAILED record when Twilio API fails")
        void sendSms_twilioApiFails() {
            // Arrange – make the Twilio SDK call throw an ApiException
            doThrow(new ApiException("Invalid phone number", 21211))
                    .when(smsService)
                    .createTwilioMessage(any(PhoneNumber.class), any(PhoneNumber.class), anyString());

            // The repository will be called twice: initial QUEUED save, then FAILED update
            SmsMessage initialSave = buildSmsMessage(1L, "+15551234567", "+15559876543",
                    "Hello!", SmsStatus.QUEUED);
            when(smsMessageRepository.save(any(SmsMessage.class))).thenReturn(initialSave);

            // Act & Assert – service must throw TwilioSmsException
            assertThatThrownBy(() -> smsService.sendSms(new SendSmsRequest("+15551234567", "Hello!")))
                    .isInstanceOf(TwilioSmsException.class)
                    .hasMessageContaining("Invalid phone number");

            // Verify the repository was saved twice: initial + failure update
            verify(smsMessageRepository, times(2)).save(any(SmsMessage.class));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // listMessages
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("listMessages")
    class ListMessagesTests {

        @Test
        @DisplayName("should return an empty list when no messages exist")
        void listMessages_empty() {
            // Arrange
            when(smsMessageRepository.findAll()).thenReturn(List.of());

            // Act
            List<SmsResponse> result = smsService.listMessages();

            // Assert
            assertThat(result).isEmpty();
            verify(smsMessageRepository).findAll();
        }

        @Test
        @DisplayName("should return mapped DTOs for all messages in the database")
        void listMessages_withResults() {
            // Arrange
            SmsMessage m1 = buildSmsMessage(1L, "+15551111111", "+15559876543", "Hi", SmsStatus.SENT);
            SmsMessage m2 = buildSmsMessage(2L, "+15552222222", "+15559876543", "Bye", SmsStatus.FAILED);
            when(smsMessageRepository.findAll()).thenReturn(List.of(m1, m2));

            // Act
            List<SmsResponse> result = smsService.listMessages();

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).toNumber()).isEqualTo("+15551111111");
            assertThat(result.get(0).status()).isEqualTo(SmsStatus.SENT);
            assertThat(result.get(1).toNumber()).isEqualTo("+15552222222");
            assertThat(result.get(1).status()).isEqualTo(SmsStatus.FAILED);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMessageById
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMessageById")
    class GetMessageByIdTests {

        @Test
        @DisplayName("should return the DTO when the ID exists")
        void getMessageById_found() {
            // Arrange
            SmsMessage msg = buildSmsMessage(42L, "+15551234567", "+15559876543", "Hi!", SmsStatus.SENT);
            when(smsMessageRepository.findById(42L)).thenReturn(Optional.of(msg));

            // Act
            SmsResponse response = smsService.getMessageById(42L);

            // Assert
            assertThat(response.id()).isEqualTo(42L);
            assertThat(response.toNumber()).isEqualTo("+15551234567");
            assertThat(response.status()).isEqualTo(SmsStatus.SENT);
        }

        @Test
        @DisplayName("should throw SmsNotFoundException when the ID does not exist")
        void getMessageById_notFound() {
            // Arrange
            when(smsMessageRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> smsService.getMessageById(99L))
                    .isInstanceOf(SmsNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMessageByTwilioSid
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMessageByTwilioSid")
    class GetMessageByTwilioSidTests {

        @Test
        @DisplayName("should return the DTO when the Twilio SID exists")
        void getMessageByTwilioSid_found() {
            // Arrange
            SmsMessage msg = buildSmsMessage(1L, "+15551234567", "+15559876543", "Hi!", SmsStatus.DELIVERED);
            msg.setTwilioSid("SMabc123");
            when(smsMessageRepository.findByTwilioSid("SMabc123")).thenReturn(Optional.of(msg));

            // Act
            SmsResponse response = smsService.getMessageByTwilioSid("SMabc123");

            // Assert
            assertThat(response.twilioSid()).isEqualTo("SMabc123");
            assertThat(response.status()).isEqualTo(SmsStatus.DELIVERED);
        }

        @Test
        @DisplayName("should throw SmsNotFoundException when the Twilio SID does not exist")
        void getMessageByTwilioSid_notFound() {
            // Arrange
            when(smsMessageRepository.findByTwilioSid("SMghost")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> smsService.getMessageByTwilioSid("SMghost"))
                    .isInstanceOf(SmsNotFoundException.class)
                    .hasMessageContaining("SMghost");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getMessagesByStatus
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMessagesByStatus")
    class GetMessagesByStatusTests {

        @Test
        @DisplayName("should return messages filtered by status")
        void getMessagesByStatus_returnsFiltered() {
            // Arrange
            SmsMessage m1 = buildSmsMessage(1L, "+15551111111", "+15559876543", "A", SmsStatus.FAILED);
            SmsMessage m2 = buildSmsMessage(2L, "+15552222222", "+15559876543", "B", SmsStatus.FAILED);
            when(smsMessageRepository.findByStatus(SmsStatus.FAILED)).thenReturn(List.of(m1, m2));

            // Act
            List<SmsResponse> result = smsService.getMessagesByStatus(SmsStatus.FAILED);

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).extracting(SmsResponse::status)
                    .containsOnly(SmsStatus.FAILED);
        }

        @Test
        @DisplayName("should return empty list when no messages match the status")
        void getMessagesByStatus_noMatches() {
            // Arrange
            when(smsMessageRepository.findByStatus(SmsStatus.DELIVERED)).thenReturn(List.of());

            // Act
            List<SmsResponse> result = smsService.getMessagesByStatus(SmsStatus.DELIVERED);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Builds a {@link SmsMessage} entity with a reflectively-set {@code id} field.
     *
     * <p>JPA entities have their ID set by the database during a persist operation.
     * In unit tests we never hit the database, so we set the ID via reflection to
     * simulate an already-persisted entity.
     */
    private SmsMessage buildSmsMessage(Long id, String to, String from, String body, SmsStatus status) {
        SmsMessage msg = new SmsMessage(to, from, body, status);
        try {
            var field = SmsMessage.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(msg, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set id on SmsMessage", e);
        }
        return msg;
    }
}
