-- Redis distributed lock — acquire
--
-- KEYS[1] = lock key   (e.g. "lock:inventory:42")
-- ARGV[1] = owner token (UUID:threadId — unique per lock attempt)
-- ARGV[2] = TTL in milliseconds
--
-- Returns 1 if lock was acquired, 0 if already held by another owner.
-- The SET NX PX command is atomic: no race between check and set.

local key   = KEYS[1]
local owner = ARGV[1]
local ttl   = tonumber(ARGV[2])

local result = redis.call('SET', key, owner, 'NX', 'PX', ttl)

if result then
    return 1  -- acquired
else
    return 0  -- contended
end
