-- FrameFlow Studio V001: apply once to an explicitly selected, empty MySQL 8.4 database.
-- This is an initial schema, not an upgrade/reset script. DDL is not transactionally reversible.
-- No accounts, templates, media or fabricated task history are seeded here.
SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE user_account (
    account_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_user_account_username (username),
    CONSTRAINT ck_user_account_username CHECK (
        CHAR_LENGTH(TRIM(username)) > 0 AND username = LOWER(username)
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE uploaded_asset (
    asset_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    media_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (asset_id),
    UNIQUE KEY uk_uploaded_asset_content_key (content_key),
    CONSTRAINT ck_uploaded_asset_content_key CHECK (CHAR_LENGTH(TRIM(content_key)) > 0),
    CONSTRAINT ck_uploaded_asset_media_type CHECK (CAST(media_type AS BINARY) IN ('image/jpeg', 'image/png')),
    CONSTRAINT ck_uploaded_asset_size CHECK (size_bytes > 0 AND size_bytes <= 8388608)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE production_task (
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    template_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_stage VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    result_ref VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    failure_kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
    failure_message VARCHAR(512) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (task_id),
    KEY idx_production_task_owner_created (owner_id, created_at DESC, task_id DESC),
    CONSTRAINT ck_production_task_revision CHECK (revision >= 0),
    CONSTRAINT ck_production_task_times CHECK (updated_at >= created_at),
    -- Every nullable operand is guarded: MySQL CHECK also accepts UNKNOWN.
    -- ascii_bin uses PAD SPACE; binary casts reject trailing spaces in machine tokens.
    CONSTRAINT ck_production_task_conclusion CHECK (
        (CAST(status AS BINARY) = 'PENDING'
            AND current_stage IS NULL AND result_ref IS NULL
            AND failure_kind IS NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR
        (CAST(status AS BINARY) = 'RUNNING'
            AND current_stage IS NOT NULL
            AND CAST(current_stage AS BINARY) IN ('CHECK_ASSETS', 'GENERATE', 'FINALIZE')
            AND result_ref IS NULL
            AND failure_kind IS NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR
        (CAST(status AS BINARY) = 'SUCCEEDED'
            AND current_stage IS NOT NULL AND CAST(current_stage AS BINARY) = 'FINALIZE'
            AND result_ref IS NOT NULL AND CHAR_LENGTH(TRIM(result_ref)) > 0
            AND failure_kind IS NULL AND failure_code IS NULL AND failure_message IS NULL)
        OR
        (CAST(status AS BINARY) = 'FAILED' AND result_ref IS NULL
            AND failure_kind IS NOT NULL
            AND failure_code IS NOT NULL AND CHAR_LENGTH(TRIM(failure_code)) > 0
            AND failure_message IS NOT NULL AND CHAR_LENGTH(TRIM(failure_message)) > 0
            AND (
                (CAST(failure_kind AS BINARY) = 'STAGE' AND current_stage IS NOT NULL
                    AND CAST(current_stage AS BINARY) IN ('CHECK_ASSETS', 'GENERATE', 'FINALIZE'))
                OR (CAST(failure_kind AS BINARY) = 'DISPATCH' AND current_stage IS NULL)
            ))
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE task_input_asset (
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    position SMALLINT NOT NULL,
    asset_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (task_id, position),
    CONSTRAINT ck_task_input_asset_position CHECK (position >= 0),
    -- Only this aggregate-internal association has a physical foreign key.
    CONSTRAINT fk_task_input_asset_task FOREIGN KEY (task_id)
        REFERENCES production_task (task_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
