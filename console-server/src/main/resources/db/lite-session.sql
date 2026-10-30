-- Additive table, intentionally independent of Spring Session and schema version 5.
CREATE TABLE IF NOT EXISTS lite_session (
  token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
  user_id BIGINT NULL,
  auth_version BIGINT NOT NULL DEFAULT 0,
  csrf_token CHAR(43) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_ms BIGINT NOT NULL,
  last_access_ms BIGINT NOT NULL,
  recent_auth_ms BIGINT NOT NULL DEFAULT 0,
  INDEX lite_session_expiry(last_access_ms),
  INDEX lite_session_user(user_id),
  CONSTRAINT lite_session_user_fk FOREIGN KEY(user_id) REFERENCES web_user(id) ON DELETE CASCADE
) ENGINE=InnoDB;
