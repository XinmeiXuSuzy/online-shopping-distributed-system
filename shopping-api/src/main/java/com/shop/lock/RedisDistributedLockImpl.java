package com.shop.lock;

import com.shop.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisDistributedLockImpl implements RedisDistributedLock {

    private final RedisTemplate<String, String> redisTemplate;

    @Qualifier("lockAcquireScript")
    private final DefaultRedisScript<Long> lockAcquireScript;

    @Qualifier("lockReleaseScript")
    private final DefaultRedisScript<Long> lockReleaseScript;

    @Override
    public boolean tryLock(String lockKey, String ownerToken, long ttlMs) {
        Long result = redisTemplate.execute(
                lockAcquireScript,
                Collections.singletonList(lockKey),
                ownerToken,
                String.valueOf(ttlMs)
        );
        boolean acquired = Long.valueOf(1L).equals(result);
        if (acquired) {
            log.debug("Lock acquired: key={}, owner={}", lockKey, ownerToken);
        }
        return acquired;
    }

    @Override
    public void lock(String lockKey, String ownerToken, long ttlMs, int maxRetries, long retryIntervalMs) {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (tryLock(lockKey, ownerToken, ttlMs)) {
                return;
            }
            if (attempt < maxRetries) {
                long sleepMs = retryIntervalMs * (1L << attempt); // exponential backoff
                log.debug("Lock contention on {}, retry {}/{} in {}ms", lockKey, attempt + 1, maxRetries, sleepMs);
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LockAcquisitionException(lockKey);
                }
            }
        }
        throw new LockAcquisitionException(lockKey);
    }

    @Override
    public boolean releaseLock(String lockKey, String ownerToken) {
        Long result = redisTemplate.execute(
                lockReleaseScript,
                Collections.singletonList(lockKey),
                ownerToken
        );
        boolean released = Long.valueOf(1L).equals(result);
        if (released) {
            log.debug("Lock released: key={}, owner={}", lockKey, ownerToken);
        } else {
            log.warn("Lock NOT released (expired or owned by another): key={}, owner={}", lockKey, ownerToken);
        }
        return released;
    }
}
