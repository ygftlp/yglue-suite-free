create table yglue.yglue_flow_model
(
    id          bigint auto_increment
        primary key,
    project_id  bigint                              not null,
    identifier  varchar(255)                        not null,
    name        varchar(255)                        not null,
    class_name  varchar(512)                        not null,
    description text                                null,
    category    varchar(128)                        null,
    version     varchar(64)                         null,
    tags_json   text                                null,
    schema_json longtext                            null,
    raw_json    longtext                            null,
    del_flag    tinyint   default 0                 null,
    create_time timestamp default CURRENT_TIMESTAMP null,
    update_time timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_flow_model_project_identifier
        unique (project_id, identifier)
);

create index idx_flow_model_project
    on yglue.yglue_flow_model (project_id);

create table yglue.yglue_flow_resolver
(
    id            bigint auto_increment
        primary key,
    project_id    bigint                              not null,
    type          varchar(255)                        not null,
    name          varchar(255)                        null,
    description   text                                null,
    category      varchar(128)                        null,
    builtin       tinyint   default 0                 null,
    config_schema longtext                            null,
    class_name    varchar(512)                        null,
    raw_json      longtext                            null,
    del_flag      tinyint   default 0                 null,
    create_time   timestamp default CURRENT_TIMESTAMP null,
    update_time   timestamp default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP,
    constraint uk_flow_resolver_type
        unique (project_id, type)
);

create index idx_flow_resolver_project
    on yglue.yglue_flow_resolver (project_id);

create table yglue.yglue_project
(
    id          bigint auto_increment
        primary key,
    project_key varchar(64)                         not null,
    name        varchar(128)                        not null,
    create_time timestamp default CURRENT_TIMESTAMP not null,
    create_by   varchar(64)                         null,
    update_time timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by   varchar(64)                         null,
    del_flag    tinyint   default 0                 not null,
    constraint project_key
        unique (project_key)
);

create table yglue.yglue_flow
(
    id                   bigint auto_increment
        primary key,
    project_id           bigint                              not null,
    code                 varchar(128)                        not null,
    name                 varchar(256)                        not null,
    latest_version_id    bigint                              null,
    create_time          timestamp default CURRENT_TIMESTAMP not null,
    create_by            varchar(64)                         null,
    update_time          timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by            varchar(64)                         null,
    del_flag             tinyint   default 0                 not null,
    published_version_id bigint                              null,
    constraint uk_flow
        unique (project_id, code),
    constraint fk_flow_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create table yglue.yglue_flow_entrypoint
(
    id                  bigint auto_increment
        primary key,
    project_id          bigint                              not null,
    flow_code           varchar(128)                        not null,
    http_method         varchar(16)                         null,
    path                varchar(512)                        not null,
    request_schema_json text                                null,
    replace_response    tinyint   default 1                 not null,
    create_time         timestamp default CURRENT_TIMESTAMP not null,
    create_by           varchar(64)                         null,
    update_time         timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by           varchar(64)                         null,
    del_flag            tinyint   default 0                 not null,
    enabled             tinyint   default 0                 null comment '是否启用',
    constraint uk_flow_entry
        unique (project_id, flow_code),
    constraint fk_flow_entry_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create table yglue.yglue_flow_sync_state
(
    id              bigint auto_increment
        primary key,
    project_id      bigint                              not null,
    flow_id         bigint                              not null,
    instance_key    varchar(128)                        not null,
    last_version_id bigint                              null,
    last_synced_at  timestamp                           null,
    create_time     timestamp default CURRENT_TIMESTAMP not null,
    create_by       varchar(64)                         null,
    update_time     timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by       varchar(64)                         null,
    del_flag        tinyint   default 0                 not null,
    constraint uk_sync_state
        unique (flow_id, instance_key),
    constraint fk_sync_flow
        foreign key (flow_id) references yglue.yglue_flow (id),
    constraint fk_sync_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create table yglue.yglue_flow_version
(
    id           bigint auto_increment
        primary key,
    flow_id      bigint                              not null,
    version_no   int                                 not null,
    content_json longtext                            not null,
    create_by    varchar(128)                        null,
    update_by    varchar(128)                        null,
    create_time  timestamp default CURRENT_TIMESTAMP not null,
    update_time  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    del_flag     tinyint   default 0                 not null,
    constraint uk_flow_ver
        unique (flow_id, version_no),
    constraint fk_ver_flow
        foreign key (flow_id) references yglue.yglue_flow (id)
);

alter table yglue.yglue_flow
    add constraint fk_flow_published_version
        foreign key (published_version_id) references yglue.yglue_flow_version (id);

create table yglue.yglue_plugin_instance
(
    id             bigint auto_increment
        primary key,
    project_id     bigint                              not null,
    instance_key   varchar(128)                        not null,
    ide_type       varchar(64)                         null,
    ide_version    varchar(32)                         null,
    status         varchar(32)                         null,
    last_heartbeat timestamp default CURRENT_TIMESTAMP not null,
    last_ip        varchar(64)                         null,
    extra_info     json                                null,
    create_time    timestamp default CURRENT_TIMESTAMP not null,
    create_by      varchar(64)                         null,
    update_time    timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by      varchar(64)                         null,
    del_flag       tinyint   default 0                 not null,
    constraint uk_plugin_instance
        unique (project_id, instance_key),
    constraint fk_plugin_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create table yglue.yglue_project_endpoint
(
    id             bigint auto_increment
        primary key,
    project_id     bigint                                not null,
    endpoint_type  varchar(16)                           not null,
    component_type varchar(32) default 'BUSINESS'        not null,
    method         varchar(16)                           null,
    path           varchar(256)                          null,
    name           varchar(128)                          not null,
    description    varchar(512)                          null,
    config_json    longtext                              null,
    create_time    timestamp   default CURRENT_TIMESTAMP not null,
    create_by      varchar(64)                           null,
    update_time    timestamp   default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by      varchar(64)                           null,
    del_flag       tinyint     default 0                 not null,
    constraint uk_project_endpoint
        unique (project_id, endpoint_type, method, path),
    constraint fk_endpoint_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create index idx_endpoint_component_type
    on yglue.yglue_project_endpoint (component_type);

create table yglue.yglue_project_metadata
(
    id           bigint auto_increment
        primary key,
    project_id   bigint                              not null,
    content_json longtext                            not null,
    create_time  timestamp default CURRENT_TIMESTAMP not null,
    create_by    varchar(64)                         null,
    update_time  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by    varchar(64)                         null,
    del_flag     tinyint   default 0                 not null,
    constraint fk_meta_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create table yglue.yglue_stat_event
(
    id           bigint auto_increment
        primary key,
    project_id   bigint                              not null,
    flow_id      bigint                              null,
    event_type   varchar(64)                         not null,
    event_ts     timestamp default CURRENT_TIMESTAMP not null,
    source       varchar(128)                        null,
    payload_json longtext                            null,
    create_time  timestamp default CURRENT_TIMESTAMP not null,
    create_by    varchar(64)                         null,
    update_time  timestamp default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    update_by    varchar(64)                         null,
    del_flag     tinyint   default 0                 not null,
    constraint fk_evt_flow
        foreign key (flow_id) references yglue.yglue_flow (id),
    constraint fk_evt_project
        foreign key (project_id) references yglue.yglue_project (id)
);

create index idx_proj_ts
    on yglue.yglue_stat_event (project_id, event_ts);

