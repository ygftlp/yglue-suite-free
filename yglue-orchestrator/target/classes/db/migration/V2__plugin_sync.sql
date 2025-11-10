CREATE TABLE yglue_plugin_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  instance_key VARCHAR(128) NOT NULL,
  ide_type VARCHAR(64),
  ide_version VARCHAR(32),
  status VARCHAR(32),
  last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_ip VARCHAR(64),
  extra_info JSON,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_plugin_instance (project_id, instance_key),
  CONSTRAINT fk_plugin_project FOREIGN KEY (project_id) REFERENCES yglue_project(id)
);

CREATE TABLE yglue_flow_sync_state (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL,
  flow_id BIGINT NOT NULL,
  instance_key VARCHAR(128) NOT NULL,
  last_version_id BIGINT,
  last_synced_at TIMESTAMP,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  create_by VARCHAR(64),
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  update_by VARCHAR(64),
  del_flag TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_sync_state (flow_id, instance_key),
  CONSTRAINT fk_sync_flow FOREIGN KEY (flow_id) REFERENCES yglue_flow(id),
  CONSTRAINT fk_sync_project FOREIGN KEY (project_id) REFERENCES yglue_project(id)
);
