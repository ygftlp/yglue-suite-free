-- Consolidated MySQL initialization script for yglue-orchestrator.
-- Source of truth: current mapper XMLs, active domain models, and non-empty Flyway migrations.
-- This script targets a fresh database and intentionally reflects the current final schema,
-- not the historical step-by-step migration path.
--
-- Notes:
-- 1. Deprecated snapshot tables from early migrations are intentionally excluded because
--    they are no longer referenced by active mapper SQL.
-- 2. The script uses utf8mb4 for full UTF-8 support.
-- 3. yglue_project_endpoint keeps nullable method/path, but adds generated key columns so
--    MySQL unique constraints still work for UPSERT and null-valued service endpoints.
-- 4. yglue_jar_class_agg keeps both jar_key and jar_id. Triggers backfill jar_id from jar_key
--    so the current delete-by-jar_id logic and query-by-jar_key logic can coexist.

CREATE DATABASE IF NOT EXISTS yglue
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE yglue;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE yglue_project (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_key VARCHAR(64) NOT NULL,
    name VARCHAR(128) NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_project_key (project_key),
    KEY idx_yglue_project_active (del_flag, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_flow (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(256) NOT NULL,
    latest_version_id BIGINT NULL,
    published_version_id BIGINT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_flow_project_code (project_id, code),
    KEY idx_yglue_flow_latest_version (latest_version_id),
    KEY idx_yglue_flow_published_version (published_version_id),
    KEY idx_yglue_flow_project_active (project_id, del_flag, create_time),
    CONSTRAINT fk_yglue_flow_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_flow_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    flow_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    content_json LONGTEXT NOT NULL,
    create_by VARCHAR(128) NULL,
    update_by VARCHAR(128) NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_flow_version_flow_no (flow_id, version_no),
    KEY idx_yglue_flow_version_flow_active (flow_id, del_flag, version_no),
    CONSTRAINT fk_yglue_flow_version_flow
        FOREIGN KEY (flow_id) REFERENCES yglue_flow (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE yglue_flow
    ADD CONSTRAINT fk_yglue_flow_published_version
        FOREIGN KEY (published_version_id) REFERENCES yglue_flow_version (id);

CREATE TABLE yglue_project_metadata (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    content_json LONGTEXT NOT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_yglue_project_metadata_project_active (project_id, del_flag, create_time),
    CONSTRAINT fk_yglue_project_metadata_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_stat_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    flow_id BIGINT NULL,
    event_type VARCHAR(64) NOT NULL,
    event_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source VARCHAR(128) NULL,
    payload_json LONGTEXT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_yglue_stat_event_project_ts (project_id, event_ts),
    KEY idx_yglue_stat_event_flow_ts (flow_id, event_ts),
    CONSTRAINT fk_yglue_stat_event_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id),
    CONSTRAINT fk_yglue_stat_event_flow
        FOREIGN KEY (flow_id) REFERENCES yglue_flow (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_plugin_instance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    instance_key VARCHAR(128) NOT NULL,
    ide_type VARCHAR(64) NULL,
    ide_version VARCHAR(32) NULL,
    status VARCHAR(32) NULL,
    last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_ip VARCHAR(64) NULL,
    extra_info JSON NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_plugin_instance_project_key (project_id, instance_key),
    KEY idx_yglue_plugin_instance_project_active (project_id, del_flag, last_heartbeat),
    CONSTRAINT fk_yglue_plugin_instance_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_flow_sync_state (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    flow_id BIGINT NOT NULL,
    instance_key VARCHAR(128) NOT NULL,
    last_version_id BIGINT NULL,
    last_synced_at TIMESTAMP NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_flow_sync_state_flow_instance (flow_id, instance_key),
    KEY idx_yglue_flow_sync_state_project_instance (project_id, instance_key, del_flag),
    CONSTRAINT fk_yglue_flow_sync_state_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id),
    CONSTRAINT fk_yglue_flow_sync_state_flow
        FOREIGN KEY (flow_id) REFERENCES yglue_flow (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_project_endpoint (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    endpoint_type VARCHAR(32) NOT NULL,
    component_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS',
    method VARCHAR(16) NULL,
    path VARCHAR(512) NULL,
    method_key VARCHAR(16) GENERATED ALWAYS AS (COALESCE(method, '')) STORED,
    path_key VARCHAR(512) GENERATED ALWAYS AS (COALESCE(path, '')) STORED,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(512) NULL,
    config_json LONGTEXT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_project_endpoint_project_key (project_id, endpoint_type, method_key, path_key),
    KEY idx_yglue_project_endpoint_project_active (project_id, del_flag, update_time),
    KEY idx_yglue_project_endpoint_component_type (component_type),
    CONSTRAINT fk_yglue_project_endpoint_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_flow_entrypoint (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    flow_code VARCHAR(128) NOT NULL,
    http_method VARCHAR(16) NULL,
    path VARCHAR(512) NOT NULL,
    request_schema_json LONGTEXT NULL,
    data_response_format TEXT NULL,
    inbound_interceptors_json TEXT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    create_by VARCHAR(64) NULL,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    update_by VARCHAR(64) NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_flow_entrypoint_project_flow (project_id, flow_code),
    KEY idx_yglue_flow_entrypoint_project_active (project_id, del_flag, path, http_method),
    CONSTRAINT fk_yglue_flow_entrypoint_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_flow_model (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    identifier VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    class_name VARCHAR(512) NOT NULL,
    description TEXT NULL,
    category VARCHAR(128) NULL,
    version VARCHAR(64) NULL,
    tags_json TEXT NULL,
    schema_json LONGTEXT NULL,
    raw_json LONGTEXT NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_flow_model_project_identifier (project_id, identifier),
    KEY idx_yglue_flow_model_project_active (project_id, del_flag, name),
    CONSTRAINT fk_yglue_flow_model_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_flow_resolver (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    type VARCHAR(255) NOT NULL,
    name VARCHAR(255) NULL,
    description TEXT NULL,
    category VARCHAR(128) NULL,
    builtin TINYINT NOT NULL DEFAULT 0,
    config_schema LONGTEXT NULL,
    class_name VARCHAR(512) NULL,
    raw_json LONGTEXT NULL,
    del_flag TINYINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_flow_resolver_project_type (project_id, type),
    KEY idx_yglue_flow_resolver_project_active (project_id, del_flag, builtin, name),
    CONSTRAINT fk_yglue_flow_resolver_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_jar_library (
    id BIGINT NOT NULL AUTO_INCREMENT,
    jar_key VARCHAR(255) NOT NULL,
    name VARCHAR(255) NULL,
    group_id VARCHAR(255) NULL,
    artifact_id VARCHAR(255) NULL,
    version VARCHAR(128) NULL,
    jar_type VARCHAR(32) NOT NULL DEFAULT 'LIBRARY',
    source VARCHAR(128) NULL,
    description VARCHAR(255) NULL,
    content_hash CHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_jar_library_key (jar_key),
    KEY idx_yglue_jar_library_gav (group_id, artifact_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_project_jar_dependency (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    group_id VARCHAR(255) NULL,
    artifact_id VARCHAR(255) NULL,
    version VARCHAR(128) NULL,
    jar_key VARCHAR(255) NOT NULL,
    scope VARCHAR(64) NULL,
    selected_flag TINYINT NOT NULL DEFAULT 0,
    is_valid TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_project_jar_dependency_project_key (project_id, jar_key),
    KEY idx_yglue_project_jar_dependency_project_active (project_id, is_valid, selected_flag),
    KEY idx_yglue_project_jar_dependency_jar_key (jar_key),
    CONSTRAINT fk_yglue_project_jar_dependency_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_project_class_agg (
    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    qualified_name VARCHAR(512) NOT NULL,
    simple_name VARCHAR(255) NULL,
    package_name VARCHAR(512) NULL,
    kind VARCHAR(32) NULL,
    source_type VARCHAR(32) NOT NULL DEFAULT 'PROJECT',
    jar_id BIGINT NULL,
    doc TEXT NULL,
    fields_json LONGTEXT NULL,
    methods_json LONGTEXT NULL,
    is_valid TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_project_class_agg_project_name (project_id, qualified_name),
    KEY idx_yglue_project_class_agg_project_active (project_id, is_valid, package_name, simple_name),
    KEY idx_yglue_project_class_agg_jar_id (jar_id),
    CONSTRAINT fk_yglue_project_class_agg_project
        FOREIGN KEY (project_id) REFERENCES yglue_project (id),
    CONSTRAINT fk_yglue_project_class_agg_jar
        FOREIGN KEY (jar_id) REFERENCES yglue_jar_library (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_jar_library_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    jar_id BIGINT NOT NULL,
    qualified_name VARCHAR(512) NOT NULL,
    simple_name VARCHAR(255) NULL,
    package_name VARCHAR(512) NULL,
    kind VARCHAR(32) NULL,
    doc TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_jar_library_class_jar_name (jar_id, qualified_name),
    KEY idx_yglue_jar_library_class_jar_package (jar_id, package_name, simple_name),
    CONSTRAINT fk_yglue_jar_library_class_jar
        FOREIGN KEY (jar_id) REFERENCES yglue_jar_library (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_jar_library_class_field (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(512) NULL,
    is_static TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_yglue_jar_library_class_field_class (class_id),
    CONSTRAINT fk_yglue_jar_library_class_field_class
        FOREIGN KEY (class_id) REFERENCES yglue_jar_library_class (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_jar_library_class_method (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    return_type VARCHAR(512) NULL,
    is_static TINYINT NOT NULL DEFAULT 0,
    parameters_json JSON NULL,
    PRIMARY KEY (id),
    KEY idx_yglue_jar_library_class_method_class (class_id),
    CONSTRAINT fk_yglue_jar_library_class_method_class
        FOREIGN KEY (class_id) REFERENCES yglue_jar_library_class (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE yglue_jar_class_agg (
    id BIGINT NOT NULL AUTO_INCREMENT,
    jar_id BIGINT NULL,
    jar_key VARCHAR(255) NOT NULL,
    qualified_name VARCHAR(512) NOT NULL,
    simple_name VARCHAR(255) NULL,
    package_name VARCHAR(512) NULL,
    kind VARCHAR(32) NULL,
    doc TEXT NULL,
    fields_json LONGTEXT NULL,
    methods_json LONGTEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_yglue_jar_class_agg_key_name (jar_key, qualified_name),
    KEY idx_yglue_jar_class_agg_jar_id (jar_id),
    KEY idx_yglue_jar_class_agg_jar_key (jar_key, package_name, simple_name),
    CONSTRAINT fk_yglue_jar_class_agg_jar
        FOREIGN KEY (jar_id) REFERENCES yglue_jar_library (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TRIGGER IF EXISTS trg_yglue_jar_class_agg_fill_jar_id_before_insert;
DROP TRIGGER IF EXISTS trg_yglue_jar_class_agg_fill_jar_id_before_update;

DELIMITER $$

CREATE TRIGGER trg_yglue_jar_class_agg_fill_jar_id_before_insert
BEFORE INSERT ON yglue_jar_class_agg
FOR EACH ROW
BEGIN
    IF NEW.jar_id IS NULL AND NEW.jar_key IS NOT NULL THEN
        SET NEW.jar_id = (
            SELECT id
            FROM yglue_jar_library
            WHERE jar_key = NEW.jar_key
            LIMIT 1
        );
    END IF;
END$$

CREATE TRIGGER trg_yglue_jar_class_agg_fill_jar_id_before_update
BEFORE UPDATE ON yglue_jar_class_agg
FOR EACH ROW
BEGIN
    IF NEW.jar_key <> OLD.jar_key OR NEW.jar_id IS NULL THEN
        SET NEW.jar_id = (
            SELECT id
            FROM yglue_jar_library
            WHERE jar_key = NEW.jar_key
            LIMIT 1
        );
    END IF;
END$$

DELIMITER ;

SET FOREIGN_KEY_CHECKS = 1;
