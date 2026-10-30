ALTER TABLE web_user ADD COLUMN IF NOT EXISTS auth_version BIGINT NOT NULL DEFAULT 1;
ALTER TABLE web_user ADD COLUMN IF NOT EXISTS password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE web_user ADD COLUMN IF NOT EXISTS reset_allowed_at TIMESTAMP(3) NULL;
ALTER TABLE web_audit_event ADD COLUMN IF NOT EXISTS details_json TEXT NULL;

CREATE TABLE IF NOT EXISTS web_authorization_lock (id INT NOT NULL PRIMARY KEY);
INSERT IGNORE INTO web_authorization_lock(id) VALUES (1);
CREATE TABLE IF NOT EXISTS web_role_binding (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  ca_name VARCHAR(128) NULL,
  ca_fingerprint CHAR(64) NULL,
  all_profiles BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_web_binding_user FOREIGN KEY (user_id) REFERENCES web_user(id),
  INDEX ix_web_binding_user (user_id),
  INDEX ix_web_binding_ca (ca_name)
);
CREATE TABLE IF NOT EXISTS web_binding_profile (
  binding_id BIGINT NOT NULL,
  profile_name VARCHAR(128) NOT NULL,
  PRIMARY KEY (binding_id, profile_name),
  CONSTRAINT fk_web_binding_profile FOREIGN KEY (binding_id) REFERENCES web_role_binding(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS web_certificate_owner (
  ca_name VARCHAR(128) NOT NULL,
  ca_fingerprint CHAR(64) NOT NULL,
  serial_number VARCHAR(160) NOT NULL,
  user_id BIGINT NOT NULL,
  profile_name VARCHAR(128) NOT NULL,
  issued_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (ca_name, ca_fingerprint, serial_number),
  INDEX ix_web_owner (user_id, ca_name, issued_at),
  CONSTRAINT fk_web_owner_user FOREIGN KEY (user_id) REFERENCES web_user(id)
);
CREATE TABLE IF NOT EXISTS web_login_throttle (
  throttle_key CHAR(64) NOT NULL PRIMARY KEY,
  attempts INT NOT NULL DEFAULT 0,
  window_start TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
);
