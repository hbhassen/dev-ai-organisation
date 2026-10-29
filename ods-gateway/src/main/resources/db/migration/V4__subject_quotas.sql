-- DEVAI-075, DEVAI-076: one atomic reservation applies both project and subject limits.
CREATE TABLE subject_quota(subject VARCHAR(128) NOT NULL, project_id BIGINT NOT NULL, period VARCHAR(7) NOT NULL, consumed BIGINT NOT NULL, reserved BIGINT NOT NULL, PRIMARY KEY(project_id,subject,period));
ALTER TABLE reservation ADD COLUMN subject VARCHAR(128);
