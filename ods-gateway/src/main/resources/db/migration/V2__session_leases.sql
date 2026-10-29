-- DEVAI-067, DEVAI-120, DEVAI-123: encrypted resumable conversations and non-replaying recovery.
ALTER TABLE job ADD COLUMN owner_instance VARCHAR(36);
ALTER TABLE job ADD COLUMN lease_until BIGINT NOT NULL DEFAULT 0;
CREATE TABLE conversation(id VARCHAR(36) PRIMARY KEY, subject VARCHAR(128) NOT NULL, expires_at BIGINT NOT NULL, revision BIGINT NOT NULL, payload TEXT NOT NULL, FOREIGN KEY(id) REFERENCES job(id));
CREATE INDEX conversation_expiry ON conversation(expires_at);
