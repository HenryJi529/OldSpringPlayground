-- 企业分类码表
insert into daas_dict (dict_key, name, code, label) values
('ENT_TYPE', '企业类型', '01', '国有企业'),
('ENT_TYPE', '企业类型', '02', '民营企业'),
('ENT_TYPE', '企业类型', '03', '外资企业'),
('ENT_TYPE', '企业类型', '04', '合资企业');

insert into daas_field_dict_mapping (daas_id, daas_field, dict_key) values
('search_enterprise', 'type', 'ENT_TYPE'),
('get_enterprise', 'type', 'ENT_TYPE');
