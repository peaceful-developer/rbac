-- New permissions for the project/multi-tenancy layer.
-- PROJECT_* (READ/WRITE/DELETE) govern the Project entities themselves - Master Admin
-- scope, checked as ordinary global authorities (see ProjectController).
-- PROJECT_MEMBER_* govern membership *within* a specific project - these are NOT
-- checked as global authorities (a Super Admin of project A must not thereby manage
-- project B's members); see ProjectAuthorizationService for the per-project check.
INSERT INTO permissions (name, description, created_at) VALUES
    ('PROJECT_READ',          'View projects',                              CURRENT_TIMESTAMP),
    ('PROJECT_WRITE',         'Create and update projects',                 CURRENT_TIMESTAMP),
    ('PROJECT_DELETE',        'Delete projects',                            CURRENT_TIMESTAMP),
    ('PROJECT_MEMBER_READ',   'View a project''s members and their roles',  CURRENT_TIMESTAMP),
    ('PROJECT_MEMBER_WRITE',  'Add members to a project or change their roles', CURRENT_TIMESTAMP),
    ('PROJECT_MEMBER_DELETE', 'Remove members from a project',              CURRENT_TIMESTAMP);

-- Keep the existing ADMIN role's "every permission" invariant now that new permissions exist.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.name IN
    ('PROJECT_READ', 'PROJECT_WRITE', 'PROJECT_DELETE',
     'PROJECT_MEMBER_READ', 'PROJECT_MEMBER_WRITE', 'PROJECT_MEMBER_DELETE');

-- SUPER_ADMIN: assigned by a Master Admin to a user *within a specific project*
-- (project_membership_roles), never globally. Holds enough to run their project day
-- to day - manage its members, and build new roles from the permission catalog a
-- Master Admin maintains - but not PERMISSION_WRITE (only Master Admin extends the
-- permission catalog itself) and not ROLE_DELETE (avoid a project admin deleting a
-- role other projects still rely on, since roles are shared, not project-scoped).
-- Locked (editable = FALSE): only a Master Admin may ever change what this role means.
INSERT INTO roles (name, description, created_at, editable) VALUES
    ('SUPER_ADMIN', 'Manages a single project''s members and roles, assigned by a Master Admin', CURRENT_TIMESTAMP, FALSE);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN' AND p.name IN
    ('PROJECT_MEMBER_READ', 'PROJECT_MEMBER_WRITE', 'PROJECT_MEMBER_DELETE',
     'ROLE_READ', 'ROLE_WRITE', 'PERMISSION_READ');

-- The existing ADMIN role is now a system-critical role in the same sense SUPER_ADMIN
-- is - lock it so only a Master Admin can redefine it.
UPDATE roles SET editable = FALSE WHERE name = 'ADMIN';

-- The seeded default account becomes the platform's first Master Admin. It keeps its
-- existing ADMIN role too (unrelated global admin-panel capability, layered under this).
UPDATE users SET master_admin = TRUE WHERE username = 'admin';
