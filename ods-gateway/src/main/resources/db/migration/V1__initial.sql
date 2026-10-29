-- DEVAI-121, DEVAI-122: immutable identities and durable operation/accounting state.
CREATE TABLE watermark(scope VARCHAR(512) PRIMARY KEY, version BIGINT NOT NULL, hash VARCHAR(64) NOT NULL);
CREATE TABLE audit(id VARCHAR(36) PRIMARY KEY, created_at VARCHAR(40) NOT NULL, subject VARCHAR(128) NOT NULL, project_id BIGINT NOT NULL, action VARCHAR(64) NOT NULL, decision VARCHAR(64) NOT NULL, correlation VARCHAR(128) NOT NULL, policy_version VARCHAR(64) NOT NULL);
CREATE INDEX audit_project_time ON audit(project_id,created_at);
CREATE TABLE job(id VARCHAR(36) PRIMARY KEY, subject VARCHAR(128) NOT NULL, project_id BIGINT NOT NULL, kind VARCHAR(64) NOT NULL, request_key VARCHAR(128) NOT NULL, payload_hash VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL, created_at VARCHAR(40) NOT NULL, result TEXT NOT NULL, UNIQUE(subject,project_id,kind,request_key));
CREATE INDEX job_state ON job(status);
CREATE TABLE quota(project_id BIGINT NOT NULL, period VARCHAR(7) NOT NULL, consumed BIGINT NOT NULL, reserved BIGINT NOT NULL, PRIMARY KEY(project_id,period));
CREATE TABLE reservation(id VARCHAR(36) PRIMARY KEY, project_id BIGINT NOT NULL, period VARCHAR(7) NOT NULL, amount BIGINT NOT NULL, state VARCHAR(32) NOT NULL, input_tokens BIGINT, output_tokens BIGINT, FOREIGN KEY(project_id,period) REFERENCES quota(project_id,period));
