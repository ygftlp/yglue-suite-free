CREATE TABLE yglue_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_key VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(128) NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE yglue_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  code VARCHAR(128) NOT NULL,
  name VARCHAR(256) NOT NULL,
  latest_version_id BIGINT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_flow (project_id, code),
  CONSTRAINT fk_flow_project FOREIGN KEY (project_id) REFERENCES yglue_project(id)
);

CREATE TABLE yglue_flow_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flow_id BIGINT NOT NULL,
  version_no INT NOT NULL,
  content_json LONGTEXT NOT NULL,
  create_by VARCHAR(128),
  update_by VARCHAR(128),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  del_flag TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_flow_ver (flow_id, version_no),
  CONSTRAINT fk_ver_flow FOREIGN KEY (flow_id) REFERENCES yglue_flow(id)
);

CREATE TABLE yglue_project_metadata (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  content_json LONGTEXT NOT NULL,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT fk_meta_project FOREIGN KEY (project_id) REFERENCES yglue_project(id)
);

CREATE TABLE yglue_stat_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  flow_id BIGINT NULL,
  event_type VARCHAR(64) NOT NULL,
  event_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  source VARCHAR(128),
  payload_json LONGTEXT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0,
  INDEX idx_proj_ts (project_id, event_ts),
  CONSTRAINT fk_evt_project FOREIGN KEY (project_id) REFERENCES yglue_project(id),
  CONSTRAINT fk_evt_flow FOREIGN KEY (flow_id) REFERENCES yglue_flow(id)
);

CREATE TABLE yglue_project_code_snapshot (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  snapshot_key VARCHAR(128) NOT NULL,
  commit_hash VARCHAR(128),
  generated_at TIMESTAMP NULL,
  status TINYINT NOT NULL DEFAULT 0,
  content_hash CHAR(64),
  ide_product VARCHAR(128),
  ide_version VARCHAR(64),
  ide_build VARCHAR(64),
  remark VARCHAR(255),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_snapshot_project_key (project_id, snapshot_key)
);

CREATE TABLE yglue_project_snapshot_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_id BIGINT NOT NULL,
  qualified_name VARCHAR(512) NOT NULL,
  simple_name VARCHAR(255),
  package_name VARCHAR(512),
  kind VARCHAR(32),
  source_type VARCHAR(32) NOT NULL DEFAULT 'PROJECT',
  jar_id BIGINT NULL,
  doc TEXT,
  UNIQUE KEY uk_snapshot_class_name (snapshot_id, qualified_name),
  KEY idx_snapshot_class_snapshot (snapshot_id)
);

CREATE TABLE yglue_project_snapshot_class_field (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(512),
  is_static TINYINT NOT NULL DEFAULT 0,
  KEY idx_snapshot_field_class (class_id)
);

CREATE TABLE yglue_project_snapshot_class_method (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  return_type VARCHAR(512),
  is_static TINYINT NOT NULL DEFAULT 0,
  parameters_json JSON,
  KEY idx_snapshot_method_class (class_id)
);

CREATE TABLE yglue_project_snapshot_dependency (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  snapshot_id BIGINT NOT NULL,
  dependency_id VARCHAR(255),
  name VARCHAR(255),
  group_id VARCHAR(255),
  artifact_id VARCHAR(255),
  version VARCHAR(128),
  coordinate VARCHAR(512),
  scope VARCHAR(64),
  jar_id BIGINT NULL,
  selected_flag TINYINT NOT NULL DEFAULT 0,
  KEY idx_snapshot_dep_snapshot (snapshot_id)
);

CREATE TABLE yglue_jar_library (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  jar_key VARCHAR(255) NOT NULL,
  name VARCHAR(255),
  group_id VARCHAR(255),
  artifact_id VARCHAR(255),
  version VARCHAR(128),
  jar_type VARCHAR(32) NOT NULL DEFAULT 'LIBRARY',
  source VARCHAR(128),
  description VARCHAR(255),
  content_hash CHAR(64),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_jar_library_key (jar_key)
);

CREATE TABLE yglue_jar_library_class (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  jar_id BIGINT NOT NULL,
  qualified_name VARCHAR(512) NOT NULL,
  simple_name VARCHAR(255),
  package_name VARCHAR(512),
  kind VARCHAR(32),
  doc TEXT,
  UNIQUE KEY uk_jar_class_name (jar_id, qualified_name)
);

CREATE TABLE yglue_jar_library_class_field (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  type VARCHAR(512),
  is_static TINYINT NOT NULL DEFAULT 0,
  KEY idx_jar_field_class (class_id)
);

CREATE TABLE yglue_jar_library_class_method (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  class_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  return_type VARCHAR(512),
  is_static TINYINT NOT NULL DEFAULT 0,
  parameters_json JSON,
  KEY idx_jar_method_class (class_id)
);
