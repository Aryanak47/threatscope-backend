-- Create Admin User for Testing
-- This script creates an admin user directly in the database for testing purposes

-- First, insert the admin user
INSERT INTO users (
    first_name, 
    last_name, 
    email, 
    password_hash, 
    email_verified, 
    created_at, 
    updated_at
) VALUES (
    'Admin',
    'User', 
    'admin@threatscope.com',
    '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', -- password: "admin123"
    true,
    NOW(),
    NOW()
) ON CONFLICT (email) DO NOTHING;

-- Get the user ID (assuming it's the last inserted or find by email)
-- Insert admin role for the user
INSERT INTO user_roles (user_id, role_name) 
SELECT u.id, 'ROLE_ADMIN' 
FROM users u 
WHERE u.email = 'admin@threatscope.com'
ON CONFLICT (user_id, role_name) DO NOTHING;

-- Optional: Also give admin user access (add USER role too)
INSERT INTO user_roles (user_id, role_name) 
SELECT u.id, 'ROLE_USER' 
FROM users u 
WHERE u.email = 'admin@threatscope.com'
ON CONFLICT (user_id, role_name) DO NOTHING;

-- Verify the admin user was created correctly
SELECT 
    u.id,
    u.email,
    u.first_name,
    u.last_name,
    ur.role_name
FROM users u
LEFT JOIN user_roles ur ON u.id = ur.user_id
WHERE u.email = 'admin@threatscope.com';

/*
Admin credentials for testing:
Email: admin@threatscope.com
Password: admin123
Roles: ROLE_ADMIN, ROLE_USER
*/
