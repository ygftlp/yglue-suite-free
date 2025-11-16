CREATE TABLE IF NOT EXISTS yglue_flow_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    description TEXT,
    category VARCHAR(128),
    version VARCHAR(64),
    tags_json TEXT,
    schema_json LONGTEXT,
    raw_json LONGTEXT,
    del_flag TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flow_model_project_identifier (project_id, identifier),
    INDEX idx_flow_model_project (project_id)
);




