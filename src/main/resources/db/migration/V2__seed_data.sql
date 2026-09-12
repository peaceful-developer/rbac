-- Seed baseline permissions
INSERT INTO permissions (name, description, created_at) VALUES
    ('USER_READ',       'View user accounts',              CURRENT_TIMESTAMP),
    ('USER_WRITE',      'Create and update user accounts', CURRENT_TIMESTAMP),
    ('USER_DELETE',     'Delete user accounts',            CURRENT_TIMESTAMP),
    ('ROLE_READ',       'View roles',                       CURRENT_TIMESTAMP),
    ('ROLE_WRITE',      'Create and update roles',          CURRENT_TIMESTAMP),
    ('ROLE_DELETE',     'Delete roles',                     CURRENT_TIMESTAMP),
    ('PERMISSION_READ', 'View permissions',                 CURRENT_TIMESTAMP),
    ('PERMISSION_WRITE','Create permissions',               CURRENT_TIMESTAMP),
    ('PERMISSION_DELETE','Delete permissions',              CURRENT_TIMESTAMP);

-- Seed baseline roles
INSERT INTO roles (name, description, created_at) VALUES
    ('ADMIN',   'Full administrative access to the IAM system', CURRENT_TIMESTAMP),
    ('MANAGER', 'Can manage users but not roles or permissions', CURRENT_TIMESTAMP),
    ('USER',    'Standard authenticated user',                   CURRENT_TIMESTAMP);

-- ADMIN gets every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN';

-- MANAGER can read/write users and read roles/permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER' AND p.name IN ('USER_READ', 'USER_WRITE', 'ROLE_READ', 'PERMISSION_READ');

-- Default admin user - username: admin / password: Admin@12345
-- CHANGE THIS PASSWORD IMMEDIATELY after first login in any non-local environment.
INSERT INTO users (username, email, password_hash, first_name, last_name, enabled, account_non_locked, created_at, updated_at)
VALUES ('admin', 'admin@iam.local', '$2b$10$Eb.F.0CJWspflJhSZMzwzOZOgSPZaOWhpg2ZTAR/90W2GZh5goVUe',
        'System', 'Administrator', TRUE, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.name = 'ADMIN';
