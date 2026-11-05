-- DEVAI-194: retain measured usage and atomic monthly budgets by signed provider and model alias.
CREATE TABLE provider_quota(provider_id VARCHAR(32) NOT NULL, project_id BIGINT NOT NULL, period VARCHAR(7) NOT NULL, consumed BIGINT NOT NULL, reserved BIGINT NOT NULL, PRIMARY KEY(provider_id,project_id,period));
ALTER TABLE reservation ADD COLUMN provider_id VARCHAR(32);
ALTER TABLE reservation ADD COLUMN model_alias VARCHAR(64);
ALTER TABLE reservation ADD COLUMN model_id VARCHAR(128);
ALTER TABLE audit ADD COLUMN provider_id VARCHAR(32);
ALTER TABLE audit ADD COLUMN model_alias VARCHAR(64);
ALTER TABLE audit ADD COLUMN model_id VARCHAR(128);
