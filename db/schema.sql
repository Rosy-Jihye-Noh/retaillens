CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE jobs (
    id                  UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    status              VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    progress            SMALLINT NOT NULL DEFAULT 0,
    video_filename      VARCHAR(255),
    video_size_byte     BIGINT,
    video_duration_sec  NUMERIC(8,2),
    recorded_at         TIMESTAMPTZ,
    heatmap             JSONB,
    error_message       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at          TIMESTAMPTZ,
    finished_at         TIMESTAMPTZ,
    CONSTRAINT chk_status   CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
    CONSTRAINT chk_progress CHECK (progress BETWEEN 0 AND 100)
);
CREATE INDEX idx_jobs_status     ON jobs(status);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);

CREATE TABLE visitors (
    id                      BIGSERIAL PRIMARY KEY,
    job_id                  UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    visitor_id              INT  NOT NULL,
    estimated_age_band      VARCHAR(20),
    estimated_gender        VARCHAR(10),
    enter_at_sec            NUMERIC(8,2) NOT NULL,
    exit_at_sec             NUMERIC(8,2),
    dwell_sec               NUMERIC(8,2),
    visited_checkout        BOOLEAN NOT NULL DEFAULT FALSE,
    checkout_dwell_sec      NUMERIC(8,2) DEFAULT 0,
    estimated_purchase      BOOLEAN NOT NULL DEFAULT FALSE,
    trajectory              JSONB   NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (job_id, visitor_id),
    CONSTRAINT chk_gender CHECK (estimated_gender IN ('male','female','unknown')),
    CONSTRAINT chk_age    CHECK (estimated_age_band IN ('child','teen','young_adult','middle','senior','unknown'))
);
CREATE INDEX idx_visitors_job      ON visitors(job_id);
CREATE INDEX idx_visitors_demo     ON visitors(estimated_age_band, estimated_gender);
CREATE INDEX idx_visitors_purchase ON visitors(estimated_purchase);


SELECT table_name FROM information_schema.tables WHERE table_schema='public';