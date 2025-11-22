CREATE TABLE IF NOT EXISTS yglue_flow_resolver (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    name VARCHAR(255),
    description TEXT,
    category VARCHAR(128),
    builtin TINYINT DEFAULT 0,
    config_schema LONGTEXT,
    class_name VARCHAR(512),
    raw_json LONGTEXT,
    del_flag TINYINT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_flow_resolver_type (project_id, type),
    INDEX idx_flow_resolver_project (project_id)
);





