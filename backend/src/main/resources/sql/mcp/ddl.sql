create table if not exists daas_dict (
    dict_key    varchar(64)  not null comment '字典类型标识，如 ENT_TYPE、SEX',
    name        varchar(64)  not null comment '类型中文名，如 企业类型',
    code        varchar(64)  not null comment '码，如 01',
    label       varchar(128) not null comment '值，如 国有企业',
    primary key (dict_key, code)
) engine=innodb default charset=utf8mb4 comment '码值表';

create table if not exists daas_field_dict_mapping (
    daas_id     varchar(64)  not null comment 'DaaS 接口标识，如 enterprise_query',
    daas_field  varchar(64)  not null comment 'DaaS 字段名，如 enterprise_type',
    dict_key    varchar(64)  not null comment '绑定的字典类型',
    primary key (daas_id, daas_field)
) engine=innodb default charset=utf8mb4 comment 'DaaS字段与码值映射表';

