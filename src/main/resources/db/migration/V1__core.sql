CREATE TABLE app_user (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, display_name VARCHAR(60) NOT NULL,
 role VARCHAR(16) NOT NULL DEFAULT 'USER', created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT user_role CHECK(role IN ('USER','ADMIN'))
) ENGINE=InnoDB;
CREATE TABLE space (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(80) NOT NULL, type VARCHAR(16) NOT NULL,
 owner_id BIGINT NOT NULL, private_owner BIGINT GENERATED ALWAYS AS (CASE WHEN type='PRIVATE' THEN owner_id ELSE NULL END) STORED UNIQUE,
 max_bytes BIGINT NOT NULL DEFAULT 104857600, max_count INT NOT NULL DEFAULT 1000,
 used_bytes BIGINT NOT NULL DEFAULT 0, used_count INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 FOREIGN KEY(owner_id) REFERENCES app_user(id), CONSTRAINT space_type CHECK(type IN ('PUBLIC','PRIVATE','TEAM')),
 CONSTRAINT space_quota CHECK(used_bytes>=0 AND used_bytes<=max_bytes AND used_count>=0 AND used_count<=max_count)
) ENGINE=InnoDB;
CREATE TABLE space_member (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, space_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
 role VARCHAR(16) NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_member(space_id,user_id), KEY ix_user_space(user_id,space_id),
 FOREIGN KEY(space_id) REFERENCES space(id), FOREIGN KEY(user_id) REFERENCES app_user(id),
 CONSTRAINT member_role CHECK(role IN ('OWNER','ADMIN','EDITOR','VIEWER'))
) ENGINE=InnoDB;
CREATE TABLE space_invite (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, space_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL UNIQUE,
 role VARCHAR(16) NOT NULL, created_by BIGINT NOT NULL, expires_at DATETIME(6) NOT NULL, used_by BIGINT NULL,
 FOREIGN KEY(space_id) REFERENCES space(id), FOREIGN KEY(created_by) REFERENCES app_user(id), FOREIGN KEY(used_by) REFERENCES app_user(id),
 CONSTRAINT invite_role CHECK(role IN ('ADMIN','EDITOR','VIEWER'))
) ENGINE=InnoDB;
CREATE TABLE picture (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, space_id BIGINT NOT NULL, uploader_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, category VARCHAR(40) NOT NULL DEFAULT '', tags JSON NOT NULL,
 object_key VARCHAR(160) NOT NULL, sha256 CHAR(64) NOT NULL, media_type VARCHAR(30) NOT NULL,
 width INT NOT NULL, height INT NOT NULL, bytes BIGINT NOT NULL,
 review_status VARCHAR(16) NOT NULL, review_reason VARCHAR(200) NOT NULL DEFAULT '', reviewer_id BIGINT NULL,
 source VARCHAR(16) NOT NULL DEFAULT 'UPLOAD', version INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 KEY ix_space_created(space_id,created_at,id), KEY ix_review_created(review_status,created_at), KEY ix_uploader_space(uploader_id,space_id),
 FOREIGN KEY(space_id) REFERENCES space(id), FOREIGN KEY(uploader_id) REFERENCES app_user(id),
 CONSTRAINT review_state CHECK(review_status IN ('PENDING','APPROVED','REJECTED')),
 CONSTRAINT picture_source CHECK(source IN ('UPLOAD','AI','DEMO'))
) ENGINE=InnoDB;
CREATE TABLE ai_job (
 id CHAR(36) PRIMARY KEY, user_id BIGINT NOT NULL, space_id BIGINT NOT NULL, idempotency_key VARCHAR(64) NOT NULL,
 prompt VARCHAR(1500) NOT NULL, title VARCHAR(100) NOT NULL, provider VARCHAR(16) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'QUEUED', provider_task_id VARCHAR(100) NULL,
 picture_id BIGINT NULL, error_code VARCHAR(50) NULL, attempts INT NOT NULL DEFAULT 0,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_job_request(user_id,idempotency_key), KEY ix_job_status(status,updated_at),
 FOREIGN KEY(user_id) REFERENCES app_user(id), FOREIGN KEY(space_id) REFERENCES space(id), FOREIGN KEY(picture_id) REFERENCES picture(id) ON DELETE SET NULL,
 CONSTRAINT job_state CHECK(status IN ('QUEUED','SUBMITTING','SUBMITTED','SUCCEEDED','FAILED','UNKNOWN'))
) ENGINE=InnoDB;
CREATE TABLE storage_gc (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, object_key VARCHAR(160) NOT NULL UNIQUE,
 attempts INT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB;
CREATE TABLE audit_event (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, actor_id BIGINT NOT NULL, space_id BIGINT NULL,
 action VARCHAR(40) NOT NULL, target_id VARCHAR(100) NOT NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), KEY ix_space_audit(space_id,created_at),
 FOREIGN KEY(actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB;
