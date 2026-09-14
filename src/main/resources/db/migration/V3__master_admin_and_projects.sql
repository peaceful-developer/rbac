-- Introduces two things:
--   1. A platform-level "master admin" flag on users - deliberately a boolean, not a
--      Role row, so it can never be assigned/removed through the normal role-assignment
--      endpoints (see UserService#promoteToMasterAdmin, which is the only way to set it).
--   2. Project-based multi-tenancy: users' roles become scoped to a Project via
--      project_memberships, rather than the previous single global role set. The roles/
--      permissions catalog itself (roles, permissions, role_permissions from V1) stays
--      global/shared across every project.

ALTER TABLE users ADD COLUMN master_admin BOOLEAN NOT NULL DEFAULT FALSE;

-- Roles a Master Admin creates are locked (not editable by anyone else, even other
-- ROLE_WRITE holders) - see RoleService for the enforcement. Roles created by anyone
-- else default to editable.
ALTER TABLE roles ADD COLUMN editable BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE projects (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(150) NOT NULL UNIQUE,
    description VARCHAR(500),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL
) ENGINE=InnoDB;

-- One row per (project, user) pair - a user's membership in that project. Their actual
-- roles within the project live in project_membership_roles below, mirroring how
-- user_roles worked pre-multi-tenancy, just with an extra level of indirection so we
-- have a natural place to hang membership-level metadata (created_at, and a stable id
-- for the join table) without composite keys everywhere.
CREATE TABLE project_memberships (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT    NOT NULL,
    user_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_project_memberships_project_user UNIQUE (project_id, user_id),
    CONSTRAINT fk_project_memberships_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_memberships_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE project_membership_roles (
    membership_id BIGINT NOT NULL,
    role_id       BIGINT NOT NULL,
    PRIMARY KEY (membership_id, role_id),
    CONSTRAINT fk_pmr_membership FOREIGN KEY (membership_id) REFERENCES project_memberships(id) ON DELETE CASCADE,
    CONSTRAINT fk_pmr_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE INDEX idx_project_memberships_project_id ON project_memberships (project_id);
CREATE INDEX idx_project_memberships_user_id ON project_memberships (user_id);
