package com.shop.lock;

import com.shop.exception.LockAcquisitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisDistributedLockTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private DefaultRedisScript<Long> lockAcquireScript;
    @Mock private DefaultRedisScript<Long> lockReleaseScript;

    @InjectMocks
    private RedisDistributedLockImpl lock;

    private static final String KEY   = "lock:inventory:42";
    private static final String OWNER = "test-token";
    private static final long   TTL   = 5000L;

    @Test
    void tryLock_returnsTrue_whenRedisReturns1() {
        when(redisTemplate.execute(eq(lockAcquireScript), anyList(), any(), any()))
                .thenReturn(1L);

        boolean acquired = lock.tryLock(KEY, OWNER, TTL);

        assertThat(acquired).isTrue();
    }

    @Test
    void tryLock_returnsFalse_whenRedisReturns0() {
        when(redisTemplate.execute(eq(lockAcquireScript), anyList(), any(), any()))
                .thenReturn(0L);

        boolean acquired = lock.tryLock(KEY, OWNER, TTL);

        assertThat(acquired).isFalse();
    }

    @Test
    void lock_succeedsOnFirstAttempt() {
        when(redisTemplate.execute(eq(lockAcquireScript), anyList(), any(), any()))
                .thenReturn(1L);

        lock.lock(KEY, OWNER, TTL, 3, 10L);

        verify(redisTemplate, times(1)).execute(eq(lockAcquireScript), anyList(), any(), any());
    }

    @Test
    void lock_throwsLockAcquisitionException_afterAllRetriesExhausted() {
        when(redisTemplate.execute(eq(lockAcquireScript), anyList(), any(), any()))
                .thenReturn(0L);

        assertThatThrownBy(() -> lock.lock(KEY, OWNER, TTL, 2, 1L))
                .isInstanceOf(LockAcquisitionException.class);

        // Expects 1 initial attempt + 2 retries = 3 total
        verify(redisTemplate, times(3)).execute(eq(lockAcquireScript), anyList(), any(), any());
    }

    @Test
    void releaseLock_returnsTrue_whenOwnerMatches() {
        when(redisTemplate.execute(eq(lockReleaseScript), anyList(), any()))
                .thenReturn(1L);

        boolean released = lock.releaseLock(KEY, OWNER);

        assertThat(released).isTrue();
    }

    @Test
    void releaseLock_returnsFalse_whenOwnerMismatch() {
        when(redisTemplate.execute(eq(lockReleaseScript), anyList(), any()))
                .thenReturn(0L);

        boolean released = lock.releaseLock(KEY, "wrong-token");

        assertThat(released).isFalse();
    }
}
