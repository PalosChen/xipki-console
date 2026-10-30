CREATE TABLE IF NOT EXISTS web_ca_enrollment (
  operation_id CHAR(36) NOT NULL PRIMARY KEY,
  owner_id BIGINT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  purpose VARCHAR(24) NOT NULL,
  status VARCHAR(32) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  public_json MEDIUMTEXT NOT NULL,
  created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY enrollment_idempotency (owner_id,idempotency_key),
  KEY enrollment_owner (owner_id,status)
);
