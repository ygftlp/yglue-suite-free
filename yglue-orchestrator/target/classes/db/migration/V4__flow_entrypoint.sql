CREATE TABLE yglue_flow_entrypoint (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    flow_code VARCHAR(128) NOT NULL,
    http_method VARCHAR(16),
    path VARCHAR(512) NOT NULL,
    replace_response TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64),
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64),
    del_flag TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_flow_entry (project_id, flow_code),
    CONSTRAINT fk_flow_entry_project FOREIGN KEY (project_id) REFERENCES yglue_project(id)
);
