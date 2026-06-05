package cn.cloudcat.user.message.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserSessionTimeoutServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private UserSessionTimeoutService.SessionTemplateServiceBridge templateBridge;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private UserSessionTimeoutService service;

    @BeforeEach
    void setUp() {
        service = new UserSessionTimeoutService(redisTemplate, templateBridge);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void refreshIdleTimerSets300sTtl() {
        service.refreshIdleTimer("CONV-001");

        verify(valueOperations).set(eq("session:timer:CONV-001"), eq("1"), eq(300L), eq(TimeUnit.SECONDS));
        verify(redisTemplate).delete("session:sat:timer:CONV-001");
    }

    @Test
    void refreshIdleTimerHandlesRedisExceptionGracefully() {
        doThrow(new RuntimeException("redis down")).when(valueOperations)
                .set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));

        assertDoesNotThrow(() -> service.refreshIdleTimer("CONV-001"));
    }

    @Test
    void startSatisfactionTimerSets120sTtl() {
        service.startSatisfactionTimer("CONV-001");

        verify(valueOperations).set(eq("session:sat:timer:CONV-001"), eq("1"), eq(120L), eq(TimeUnit.SECONDS));
    }

    @Test
    void cancelTimersDeletesBothKeys() {
        service.cancelTimers("CONV-001");

        verify(redisTemplate).delete("session:timer:CONV-001");
        verify(redisTemplate).delete("session:sat:timer:CONV-001");
    }

    @Test
    void getIdleRemainingReturnsTtlWhenPositive() {
        when(redisTemplate.getExpire("session:timer:CONV-001", TimeUnit.SECONDS)).thenReturn(120L);

        assertEquals(120L, service.getIdleRemainingSeconds("CONV-001"));
    }

    @Test
    void getIdleRemainingReturnsNegativeOneWhenExpired() {
        when(redisTemplate.getExpire("session:timer:CONV-001", TimeUnit.SECONDS)).thenReturn(0L);

        assertEquals(-1L, service.getIdleRemainingSeconds("CONV-001"));
    }

    @Test
    void isIdleExpiredTrueWhenRemainingZeroOrLess() {
        when(redisTemplate.getExpire("session:timer:CONV-001", TimeUnit.SECONDS)).thenReturn(0L);
        assertTrue(service.isIdleExpired("CONV-001"));

        when(redisTemplate.getExpire("session:timer:CONV-001", TimeUnit.SECONDS)).thenReturn(-2L);
        assertTrue(service.isIdleExpired("CONV-001"));
    }

    @Test
    void isSatisfactionExpiredTrueWhenKeyAbsent() {
        when(redisTemplate.getExpire("session:sat:timer:CONV-001", TimeUnit.SECONDS)).thenReturn(-2L);

        assertTrue(service.isSatisfactionExpired("CONV-001"));
    }

    @Test
    void timeoutMessageDelegatesToBridge() {
        when(templateBridge.timeoutMessage()).thenReturn("由于您长时间未回复...");

        assertEquals("由于您长时间未回复...", service.timeoutMessage());
        verify(templateBridge).timeoutMessage();
    }
}
