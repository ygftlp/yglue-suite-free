ALTER TABLE yglue_flow
    ADD COLUMN published_version_id BIGINT NULL;

ALTER TABLE yglue_flow
    ADD CONSTRAINT fk_flow_published_version FOREIGN KEY (published_version_id) REFERENCES yglue_flow_version(id);
