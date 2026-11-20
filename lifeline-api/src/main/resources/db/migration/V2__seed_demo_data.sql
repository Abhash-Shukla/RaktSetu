-- =============================================================================
-- LifeLine (RaktSetu) Demo Seed Data :: V2__seed_demo_data.sql
-- Strictly synthetic data for development, testing and demonstration only.
-- Zero real personal, clinical, or hospital data.
-- =============================================================================

-- 1. Initial Engine Configuration (Version 1 — Locked-spec S6 Baseline)
-- Password for all demo accounts: 'password123' ($2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy)
INSERT INTO users (id, username, email, password_hash, role, status) VALUES
(1, 'admin', 'admin@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN', 'ACTIVE'),
(2, 'staff_central', 'staff1@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'BANK_STAFF', 'ACTIVE'),
(3, 'staff_metro', 'staff2@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'BANK_STAFF', 'ACTIVE'),
(4, 'donor_arjun', 'donor1@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'DONOR', 'ACTIVE'),
(5, 'donor_priya', 'donor2@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'DONOR', 'ACTIVE'),
(6, 'donor_rohit', 'donor3@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'DONOR', 'ACTIVE'),
(7, 'donor_sneha', 'donor4@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'DONOR', 'ACTIVE'),
(8, 'donor_vikram', 'donor5@lifeline.test', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'DONOR', 'ACTIVE');

SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));

-- 2. Initial Active Engine Configuration Version (P-28 Locked S6 Specifications)
INSERT INTO engine_config_versions (id, version_number, description, config_json, author_id, is_active) VALUES
(1, 1, 'Default locked-specification S6 matching parameters (P-28, P-10, P-13, P-24)',
'{"topN": 10, "cooldownDaysMale": 90, "cooldownDaysFemale": 120, "freshnessWindowHours": 24, "tier0SearchRadiusKm": 25.0, "weights": {"CRITICAL": {"proximity": 0.85, "rarity": 0.15, "responsiveness": 0.0}, "URGENT": {"proximity": 0.65, "rarity": 0.35, "responsiveness": 0.0}, "ROUTINE": {"proximity": 0.40, "rarity": 0.60, "responsiveness": 0.0}}}',
1, TRUE);

SELECT setval('engine_config_versions_id_seq', (SELECT MAX(id) FROM engine_config_versions));

-- 3. Synthetic Donor Profiles
INSERT INTO donor_profiles (id, user_id, blood_group, sex, home_zone, last_donation_date, is_available, is_paused, current_zone, location_confirmed_at, total_contacts, total_accepts, total_declines, total_no_responses) VALUES
(1, 4, 'O_POS', 'MALE', 'Z1', '2026-06-01', TRUE, FALSE, 'Z1', CURRENT_TIMESTAMP, 3, 2, 1, 0),
(2, 5, 'A_POS', 'FEMALE', 'Z1', '2026-05-15', TRUE, FALSE, 'Z1', CURRENT_TIMESTAMP, 2, 2, 0, 0),
(3, 6, 'B_POS', 'MALE', 'Z2', '2026-07-20', TRUE, FALSE, 'Z2', CURRENT_TIMESTAMP, 4, 3, 1, 0),
(4, 7, 'O_NEG', 'FEMALE', 'Z1', '2026-04-10', TRUE, FALSE, 'Z1', CURRENT_TIMESTAMP, 5, 4, 1, 0),
(5, 8, 'AB_POS', 'MALE', 'Z3', '2026-08-01', TRUE, FALSE, 'Z3', CURRENT_TIMESTAMP, 1, 1, 0, 0);

SELECT setval('donor_profiles_id_seq', (SELECT MAX(id) FROM donor_profiles));

-- 4. Synthetic Blood Banks
INSERT INTO banks (id, name, zone, contact_phone, contact_email, latitude, longitude) VALUES
(1, 'Central City Blood Bank', 'Z1', '+91-9876543210', 'central@bloodbank.test', 28.6139, 77.2090),
(2, 'Metro Red Cross Center', 'Z2', '+91-9876543211', 'metro@bloodbank.test', 28.7041, 77.1025),
(3, 'Apex Medical Blood Repository', 'Z3', '+91-9876543212', 'apex@bloodbank.test', 28.5355, 77.3910);

SELECT setval('banks_id_seq', (SELECT MAX(id) FROM banks));

-- 5. Synthetic Bank Inventory
INSERT INTO bank_inventory (bank_id, blood_group, units_available) VALUES
(1, 'O_POS', 12),
(1, 'A_POS', 8),
(1, 'B_POS', 6),
(1, 'O_NEG', 2),
(1, 'AB_POS', 3),
(2, 'O_POS', 5),
(2, 'B_POS', 10),
(2, 'A_NEG', 1),
(3, 'A_POS', 4),
(3, 'O_POS', 7),
(3, 'AB_NEG', 1);

-- 6. Initial Audit Log Entry
INSERT INTO audit_log (actor_username, actor_role, action, entity_type, entity_id, details) VALUES
('SYSTEM', 'SYSTEM', 'SYSTEM_INITIALIZATION', 'SYSTEM', '1', 'Initial synthetic database schema and seed data loaded successfully.');
