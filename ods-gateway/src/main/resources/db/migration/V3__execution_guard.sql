-- DEVAI-079, DEVAI-120: cluster-wide per-subject execution limits.
CREATE TABLE execution_guard(subject VARCHAR(128) PRIMARY KEY, period BIGINT NOT NULL, calls INTEGER NOT NULL);
