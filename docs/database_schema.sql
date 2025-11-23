-- =====================================================
-- YGFlow Suite 数据库完整脚本
-- 版本: 0.1.0-SNAPSHOT
-- 数据库: MySQL 8.0+
-- =====================================================

-- 创建数据库（可选，如果数据库不存在）
-- CREATE DATABASE IF NOT EXISTS ygflow DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE ygflow;

-- =====================================================
-- 1. 项目表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_project (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_key VARCHAR(64) NOT NULL UNIQUE COMMENT '项目标识',
  name VARCHAR(128) NOT NULL COMMENT '项目名称',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  INDEX idx_project_key (project_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- =====================================================
-- 2. 流程表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_flow (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  code VARCHAR(128) NOT NULL COMMENT '流程代码',
  name VARCHAR(256) NOT NULL COMMENT '流程名称',
  latest_version_id BIGINT NULL COMMENT '最新版本ID',
  published_version_id BIGINT NULL COMMENT '已发布版本ID',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  UNIQUE KEY uk_flow (project_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程表';

-- =====================================================
-- 3. 流程版本表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_flow_version (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  flow_id BIGINT NOT NULL COMMENT '流程ID',
  version_no INT NOT NULL COMMENT '版本号',
  content_json LONGTEXT NOT NULL COMMENT '流程内容（JSON格式）',
  create_by VARCHAR(128) COMMENT '创建人',
  update_by VARCHAR(128) COMMENT '更新人',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  UNIQUE KEY uk_flow_ver (flow_id, version_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程版本表';

-- 注意：published 状态通过 yglue_flow.published_version_id 关联判断，不在本表中存储

-- =====================================================
-- 4. 流程入口点表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_flow_entrypoint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  flow_code VARCHAR(128) NOT NULL COMMENT '流程代码',
  http_method VARCHAR(16) COMMENT 'HTTP方法',
  path VARCHAR(512) NOT NULL COMMENT '路径',
  request_schema_json TEXT NULL COMMENT '请求Schema（JSON格式）',
  data_response_format TEXT NULL COMMENT '数据响应格式配置（JSON字符串或预设名称）',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  UNIQUE KEY uk_flow_entry (project_id, flow_code),
  INDEX idx_entrypoint_path_method (project_id, path, http_method)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程入口点表';

-- =====================================================
-- 5. 项目端点表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_project_endpoint (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  endpoint_type VARCHAR(16) NOT NULL COMMENT '端点类型：REST等',
  component_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS' COMMENT '组件类型：BUSINESS-业务，SYSTEM-系统',
  method VARCHAR(16) COMMENT 'HTTP方法',
  path VARCHAR(256) COMMENT '路径',
  name VARCHAR(128) NOT NULL COMMENT '端点名称',
  description VARCHAR(512) COMMENT '描述',
  config_json LONGTEXT COMMENT '配置信息（JSON格式）',
  request_schema_json TEXT COMMENT '请求Schema（JSON格式）',
  enabled TINYINT DEFAULT 1 COMMENT '是否启用：0-禁用，1-启用',
  flow_code VARCHAR(128) COMMENT '关联的流程代码',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  UNIQUE KEY uk_project_endpoint (project_id, endpoint_type, method, path),
  INDEX idx_endpoint_component_type (component_type),
  INDEX idx_endpoint_flow_code (project_id, flow_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目端点表';

-- 注意：component_type 字段在 V3__endpoint_component_type.sql 中添加
-- 如果表已存在但缺少该字段，需要执行：
-- ALTER TABLE yglue_project_endpoint ADD COLUMN component_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS' AFTER endpoint_type;

-- =====================================================
-- 6. 项目元数据表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_project_metadata (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  content_json LONGTEXT NOT NULL COMMENT '元数据内容（JSON格式）',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目元数据表';

-- =====================================================
-- 7. 统计事件表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_stat_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  flow_id BIGINT NULL COMMENT '流程ID',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  event_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  source VARCHAR(128) COMMENT '事件来源',
  payload_json LONGTEXT COMMENT '事件负载（JSON格式）',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  INDEX idx_proj_ts (project_id, event_ts),
  INDEX idx_event_type (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='统计事件表';

-- =====================================================
-- 8. 插件实例表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_plugin_instance (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  instance_key VARCHAR(128) NOT NULL COMMENT '实例标识',
  ide_type VARCHAR(64) COMMENT 'IDE类型',
  ide_version VARCHAR(32) COMMENT 'IDE版本',
  status VARCHAR(32) COMMENT '状态',
  last_heartbeat TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后心跳时间',
  last_ip VARCHAR(64) COMMENT '最后IP地址',
  extra_info JSON COMMENT '额外信息（JSON格式）',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  UNIQUE KEY uk_plugin_instance (project_id, instance_key),
  INDEX idx_plugin_heartbeat (project_id, last_heartbeat)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='插件实例表';

-- =====================================================
-- 9. 流程同步状态表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_flow_sync_state (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  flow_id BIGINT NOT NULL COMMENT '流程ID',
  instance_key VARCHAR(128) NOT NULL COMMENT '插件实例标识',
  last_version_id BIGINT COMMENT '最后同步版本ID',
  last_synced_at TIMESTAMP COMMENT '最后同步时间',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  create_by VARCHAR(64) COMMENT '创建人',
  update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  update_by VARCHAR(64) COMMENT '更新人',
  del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  UNIQUE KEY uk_sync_state (flow_id, instance_key),
  INDEX idx_sync_instance (project_id, instance_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流程同步状态表';

-- =====================================================
-- 10. Flow Model 表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_flow_model (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  identifier VARCHAR(255) NOT NULL COMMENT '模型标识',
  name VARCHAR(255) NOT NULL COMMENT '模型名称',
  class_name VARCHAR(512) NOT NULL COMMENT '类名',
  description TEXT COMMENT '描述',
  category VARCHAR(128) COMMENT '分类',
  version VARCHAR(64) COMMENT '版本',
  tags_json TEXT COMMENT '标签（JSON格式）',
  schema_json LONGTEXT COMMENT 'Schema（JSON格式）',
  raw_json LONGTEXT COMMENT '原始JSON',
  del_flag TINYINT DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_flow_model_project_identifier (project_id, identifier),
  INDEX idx_flow_model_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flow Model表';

-- =====================================================
-- 11. Flow Resolver 表
-- =====================================================
CREATE TABLE IF NOT EXISTS yglue_flow_resolver (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  project_id BIGINT NOT NULL COMMENT '项目ID',
  type VARCHAR(255) NOT NULL COMMENT '解析器类型',
  name VARCHAR(255) COMMENT '解析器名称',
  description TEXT COMMENT '描述',
  category VARCHAR(128) COMMENT '分类',
  builtin TINYINT DEFAULT 0 COMMENT '是否内置：0-否，1-是',
  config_schema LONGTEXT COMMENT '配置Schema（JSON格式）',
  class_name VARCHAR(512) COMMENT '类名',
  raw_json LONGTEXT COMMENT '原始JSON',
  del_flag TINYINT DEFAULT 0 COMMENT '删除标志：0-未删除，1-已删除',
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_flow_resolver_type (project_id, type),
  INDEX idx_flow_resolver_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Flow Resolver表';

-- =====================================================
-- 初始化数据（可选）
-- =====================================================

-- 示例：创建默认项目
-- INSERT INTO yglue_project (project_key, name, create_by) VALUES ('default', '默认项目', 'system');

-- =====================================================
-- 补充说明
-- =====================================================
-- 1. yglue_flow_version 表的 published 状态通过 yglue_flow.published_version_id 关联判断
--    不在数据库中直接存储 published 字段，而是通过业务逻辑计算
-- 
-- 2. yglue_project_endpoint 表的 component_type 字段在 V3__endpoint_component_type.sql 中添加
--    如果表已存在但缺少该字段，需要执行：
--    ALTER TABLE yglue_project_endpoint ADD COLUMN component_type VARCHAR(32) NOT NULL DEFAULT 'BUSINESS' AFTER endpoint_type;
--    CREATE INDEX idx_endpoint_component_type ON yglue_project_endpoint (component_type);
-- 
-- 3. yglue_flow_entrypoint 表的 data_response_format 字段由 error_response_format 重命名而来
--    如果表已存在但字段名为 error_response_format，需要执行：
--    ALTER TABLE yglue_flow_entrypoint CHANGE COLUMN error_response_format data_response_format TEXT NULL COMMENT '数据响应格式配置（JSON 字符串或预设名称）';
-- =====================================================
-- 脚本说明
-- =====================================================
-- 1. 本脚本包含 YGFlow Suite 所有数据库表的创建语句
-- 2. 表创建顺序已优化，但无外键约束（便于数据迁移和性能优化）
-- 3. 所有表都包含软删除标志（del_flag）
-- 4. 所有表都包含创建时间和更新时间字段
-- 5. 数据关联关系通过业务逻辑维护，不在数据库层面强制约束
-- 6. 建议在生产环境执行前先备份现有数据
-- 7. 本脚本基于 Flyway 迁移脚本整理，版本号：V1-V7
-- =====================================================

