-- ==============================================================================
-- LifeLine / RaktSetu Database Schema Migration V3
-- Adds NLP free-text intake columns to blood_requests:
-- contact_number, unparsed_text, extraction_confidence
-- ==============================================================================

ALTER TABLE blood_requests
    ADD COLUMN contact_number VARCHAR(100),
    ADD COLUMN unparsed_text TEXT,
    ADD COLUMN extraction_confidence DOUBLE PRECISION;
