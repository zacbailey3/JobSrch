ALTER TABLE indexed_jobs
    ADD COLUMN opportunity_type VARCHAR(30) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE indexed_jobs
    ADD COLUMN career_stage VARCHAR(30) NOT NULL DEFAULT 'UNSPECIFIED';
ALTER TABLE indexed_jobs
    ADD COLUMN degree_requirement VARCHAR(40) NOT NULL DEFAULT 'NOT_STATED';
ALTER TABLE indexed_jobs
    ADD COLUMN sponsorship_status VARCHAR(30) NOT NULL DEFAULT 'NOT_STATED';

ALTER TABLE saved_searches
    ADD COLUMN opportunity_type VARCHAR(30);
ALTER TABLE saved_searches
    ADD COLUMN career_stage VARCHAR(30);
ALTER TABLE saved_searches
    ADD COLUMN degree_requirement VARCHAR(40);
ALTER TABLE saved_searches
    ADD COLUMN sponsorship_status VARCHAR(30);
ALTER TABLE saved_searches
    ADD COLUMN maximum_experience INT;
