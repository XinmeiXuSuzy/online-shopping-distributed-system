-- Redis distributed lock — release
--
-- KEYS[1] = lock key
-- ARGV[1] = owner token (must match the value stored at the key)
--
-- Returns 1 if the lock was released by this caller.
-- Returns 0 if the lock was already expired or held by a different owner.
--
-- The GET + DEL sequence is atomic inside Lua — no other client can
-- acquire the lock between the check and the delete.

local key   = KEYS[1]
local owner = ARGV[1]

local current = redis.call('GET', key)

if current == owner then
    redis.call('DEL', key)
    return 1  -- released
else
    return 0  -- not owner or already expired
end
