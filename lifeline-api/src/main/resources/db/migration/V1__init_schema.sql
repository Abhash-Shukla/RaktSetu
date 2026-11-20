-- =============================================================================
-- LifeLine (RaktSetu) Database Schema :: V1__init_schema.sql
-- PostgreSQL Flyway Migration
-- =============================================================================

-- 1. Users table (DONOR, BANK_STAFF, ADMIN)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- 2. Donor Profiles table
CREATE TABLE donor_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    blood_group VARCHAR(10) NOT NULL,
    sex VARCHAR(10) NOT NULL,
    home_zone VARCHAR(50) NOT NULL,
    last_donation_date DATE,
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    is_paused BOOLEAN NOT NULL DEFAULT FALSE,
    current_zone VARCHAR(50),
    location_confirmed_at TIMESTAMP WITH TIME ZONE,
    total_contacts INT NOT NULL DEFAULT 0,
    total_accepts INT NOT NULL DEFAULT 0,
    total_declines INT NOT NULL DEFAULT 0,
    total_no_responses INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_donor_profiles_blood_group ON donor_profiles(blood_group);
CREATE INDEX idx_donor_profiles_home_zone ON donor_profiles(home_zone);
CREATE INDEX idx_donor_profiles_availability ON donor_profiles(is_available, is_paused);

-- 3. Blood Requests table
CREATE TABLE blood_requests (
    id BIGSERIAL PRIMARY KEY,
    patient_reference VARCHAR(100) NOT NULL,
    blood_group VARCHAR(10) NOT NULL,
    units_requested INT NOT NULL,
    units_fulfilled INT NOT NULL DEFAULT 0,
    urgency VARCHAR(20) NOT NULL,
    zone VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    rejection_reason TEXT,
    source VARCHAR(20) NOT NULL DEFAULT 'STAFF',
    reviewer_id BIGINT REFERENCES users(id),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    deadline TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_blood_requests_status ON blood_requests(status);
CREATE INDEX idx_blood_requests_zone ON blood_requests(zone);
CREATE INDEX idx_blood_requests_urgency ON blood_requests(urgency);

-- 4. Blood Banks table
CREATE TABLE banks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    zone VARCHAR(50) NOT NULL,
    contact_phone VARCHAR(50),
    contact_email VARCHAR(100),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_banks_zone ON banks(zone);

-- 5. Bank Inventory table
CREATE TABLE bank_inventory (
    id BIGSERIAL PRIMARY KEY,
    bank_id BIGINT NOT NULL REFERENCES banks(id) ON DELETE CASCADE,
    blood_group VARCHAR(10) NOT NULL,
    units_available INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bank_inventory UNIQUE (bank_id, blood_group),
    CONSTRAINT chk_units_available_non_negative CHECK (units_available >= 0)
);

CREATE INDEX idx_bank_inventory_bank_bg ON bank_inventory(bank_id, blood_group);

-- 6. Engine Configuration Versions table
CREATE TABLE engine_config_versions (
    id BIGSERIAL PRIMARY KEY,
    version_number INT NOT NULL UNIQUE,
    description TEXT,
    config_json TEXT NOT NULL,
    author_id BIGINT REFERENCES users(id),
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_engine_config_active ON engine_config_versions(is_active);

-- 7. Match Logs table
CREATE TABLE match_logs (
    id BIGSERIAL PRIMARY KEY,
    request_id BIGINT NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
    step_number INT NOT NULL,
    donor_id BIGINT NOT NULL REFERENCES donor_profiles(id) ON DELETE CASCADE,
    rank_position INT NOT NULL,
    total_score DOUBLE PRECISION NOT NULL,
    factor_breakdown TEXT NOT NULL,
    explanation_text TEXT NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    outcome VARCHAR(30) NOT NULL DEFAULT 'NOTIFIED',
    config_version_id BIGINT REFERENCES engine_config_versions(id),
    notified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_match_logs_request ON match_logs(request_id);
CREATE INDEX idx_match_logs_donor ON match_logs(donor_id);
CREATE INDEX idx_match_logs_outcome ON match_logs(outcome);

-- 8. Audit Log table
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_username VARCHAR(100),
    actor_role VARCHAR(50),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    details TEXT,
    ip_address VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_timestamp ON audit_log(created_at);
CREATE INDEX idx_audit_log_actor ON audit_log(actor_username);
CREATE INDEX idx_audit_log_action ON audit_log(action);
