-- Step 2: Remove directly inserted test user
DELETE FROM users WHERE email = 'test@dolphin.ai';

-- Step 2b: Restore admin password to the known DEMO_PASSWORD from .env (dolphin123)
-- Using $2a$ prefix for Spring Security BCryptPasswordEncoder compatibility
UPDATE users SET password = '$2a$10$uKMMNT9YUUk5YWZjQyffzON.GyVH6Y7sfpuvQKoaMKSCKaBnsQpnW' WHERE email = 'admin@dolphin.ai';

-- Verify cleanup
SELECT id, email, role, active, created_at FROM users ORDER BY created_at DESC;
