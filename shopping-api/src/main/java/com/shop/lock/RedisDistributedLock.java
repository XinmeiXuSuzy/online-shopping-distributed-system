package com.shop.lock;

/**
 * Distributed lock backed by Redis using atomic Lua scripts.
 * Callers must always release the lock in a finally block.
 */
public interface RedisDistributedLock {

    /**
     * Try to acquire the lock for the given key.
     *
     * @param lockKey    the Redis key (e.g. "lock:inventory:42")
     * @param ownerToken unique caller token; must be provided to releaseLock
     * @param ttlMs      lock TTL in milliseconds; auto-expires to prevent deadlocks
     * @return true if lock was acquired
     */
    boolean tryLock(String lockKey, String ownerToken, long ttlMs);

    /**
     * Try to acquire the lock, retrying up to maxRetries times with backoff.
     *
     * @throws com.shop.exception.LockAcquisitionException if all retries fail
     */
    void lock(String lockKey, String ownerToken, long ttlMs, int maxRetries, long retryIntervalMs);

    /**
     * Release the lock only if this caller still owns it.
     * Safe to call even if the lock has already expired.
     *
     * @return true if the lock was released by this caller
     */
    boolean releaseLock(String lockKey, String ownerToken);
}
