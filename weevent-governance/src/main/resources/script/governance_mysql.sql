CREATE TABLE t_account(
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(256) NOT NULL COMMENT '密码`',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `delete_at` BIGINT(16) NOT NULL DEFAULT  0 COMMENT '0 表示 未删除, 时间戳 表示 已经被删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment '用户表';


CREATE TABLE t_broker (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `user_id` INT(11) NOT NULL  COMMENT 'user主键id',
  `name` VARCHAR(256) NOT NULL COMMENT '名称',
  `broker_url` VARCHAR(256) DEFAULT NULL COMMENT 'broker url',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `delete_at` BIGINT(16) NOT NULL DEFAULT  0 COMMENT '0 表示 未删除, 时间戳 表示 已经被删除',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment 'broker配置表';

CREATE TABLE  t_topic (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `topic_name` VARCHAR(256) NOT NULL COMMENT '主题名称',
  `creater` VARCHAR(256) DEFAULT NULL COMMENT '创建人',
  `broker_id` INT(11) NOT NULL COMMENT 'broker主键id',
  `group_id` VARCHAR(64) DEFAULT NULL COMMENT '群组id',
  `description` VARCHAR(256)  NULL  DEFAULT NULL COMMENT '主题描述',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '修改日期',
  `delete_at` BIGINT(16) NOT NULL DEFAULT  0 COMMENT '0 表示 未删除, 时间戳 表示 已经被删除',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment '主题表';

CREATE TABLE  t_file_transport_channel (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `topic_name` VARCHAR(256) NOT NULL COMMENT '主题名称',
  `creater` VARCHAR(256) DEFAULT NULL COMMENT '创建人',
  `broker_id` INT(11) NOT NULL COMMENT 'broker主键id',
  `group_id` VARCHAR(64) DEFAULT NULL COMMENT '群组id',
  `description` VARCHAR(256) DEFAULT NULL COMMENT '主题描述',
  `role` VARCHAR(1) NOT NULL COMMENT '文件传输角色, 0:接收方 1:发布方',
  `over_write` VARCHAR(1) NOT NULL COMMENT '是否覆盖文件, 0:否 1:是',
  `node_address` VARCHAR(64) NOT NULL COMMENT '节点地址',
  `public_key` text DEFAULT NULL COMMENT '接收方公钥',
  `private_key` text DEFAULT NULL COMMENT '接收方私钥',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '修改日期',
  `delete_at` BIGINT(16) NOT NULL DEFAULT  0 COMMENT '0 表示 未删除, 时间戳 表示 已经被删除',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment '传输通道表';

CREATE TABLE  t_file_transport_status (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `topic_name` VARCHAR(256) NOT NULL COMMENT '主题名称',
  `node_address` VARCHAR(64) NOT NULL COMMENT '节点地址',
  `creater` VARCHAR(256) DEFAULT NULL COMMENT '创建人',
  `broker_id` INT(11) NOT NULL COMMENT 'broker主键id',
  `group_id` VARCHAR(64) DEFAULT NULL COMMENT '群组id',
  `description` VARCHAR(256) DEFAULT NULL COMMENT '主题描述',
  `file_name` VARCHAR(256) NOT NULL COMMENT '文件名',
  `file_md5` VARCHAR(32) NOT NULL COMMENT '文件MD5',
  `file_size` BIGINT(16) NOT NULL COMMENT '文件大小',
  `transport_status` VARCHAR(1) DEFAULT NULL COMMENT '文件传输状态',
  `speed` VARCHAR(32) DEFAULT NULL COMMENT '文件传输速率',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '修改日期',
  `delete_at` BIGINT(16) NOT NULL DEFAULT  0 COMMENT '0 表示 未删除, 时间戳 表示 已经被删除',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment '文件传输状态表';


CREATE TABLE t_permission (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `broker_id` INT(11) NOT NULL COMMENT 'broker主键id',
  `user_id` INT(11) NOT NULL COMMENT 'user主键id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment 'broker授权表';

CREATE TABLE t_rule_engine (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `rule_name` VARCHAR(128) NOT  NULL   COMMENT '规则名称',
  `payload_type` INT(4)  NULL DEFAULT NULL COMMENT '1表示JASON',
  `payload` VARCHAR(4096)  NULL DEFAULT NULL COMMENT '主体内容',
  `rule_description` VARCHAR(256)  NULL DEFAULT NULL COMMENT '规则描述',
  `broker_id` INT(11) not NULL COMMENT 'broker主键id',
  `user_id` INT(11) not NULL COMMENT 'user主键id',
  `group_id` VARCHAR(64) not NULL COMMENT '群组id',
  `cep_id` VARCHAR(64) NULL COMMENT '规则id',
  `from_destination` VARCHAR(64)  NULL DEFAULT NULL COMMENT  '数据来源',
  `to_destination` VARCHAR(64)  NULL DEFAULT NULL COMMENT  '数据目的',
  `function_array` VARCHAR(1024)  NULL DEFAULT NULL COMMENT  '函数数组字符串',
  `condition_field` VARCHAR(4096)  NULL DEFAULT NULL COMMENT  '过滤条件',
  `condition_field_json` VARCHAR(4096)  NULL DEFAULT NULL COMMENT  '过滤条件Json',
  `select_field` VARCHAR(4096) NULL DEFAULT NULL COMMENT  '选择字段',
  `condition_type` INT(2) NULL DEFAULT NULL COMMENT '数据流转类型',
  `status` INT(2)  NULL DEFAULT null COMMENT '0 未启动, 1 运行,2 已经删除',
  `database_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '数据库 url',
  `rule_database_id` INT(11) NULL DEFAULT NULL COMMENT '数据源 主键id',
  `error_destination` VARCHAR(255) NULL DEFAULT NULL COMMENT '失败流转目的地',
  `system_tag` VARCHAR(1) NOT NULL DEFAULT '1' COMMENT '1 系统内置 ,2 用户新增',
  `table_name` VARCHAR(128)  NULL DEFAULT NULL COMMENT '表格名称',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `delete_at` BIGINT(16) NOT NULL DEFAULT  0 COMMENT '0 表示 未删除, 时间戳 表示 已经被删除',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='规则引擎表';

CREATE TABLE t_rule_database (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
  `database_type` VARCHAR(1) NOT NULL COMMENT '数据源类型,1代表h2,2代表mysql',
  `datasource_name` VARCHAR(256) NOT NULL COMMENT '数据源名称',
  `database_url` VARCHAR(128) NOT NULL COMMENT '数据库url',
  `database_ip` VARCHAR(32) NULL DEFAULT NULL COMMENT '数据库ip',
  `database_port` VARCHAR(32) NULL DEFAULT NULL COMMENT '数据库port',
  `database_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '数据库名称',
  `username` VARCHAR(16) NOT NULL COMMENT '数据库用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '数据库密码',
  `optional_parameter` VARCHAR(256) DEFAULT NULL COMMENT '数据库可选参数',
  `broker_id` VARCHAR(256) DEFAULT NULL COMMENT 'broker主键id',
  `user_id` VARCHAR(256) DEFAULT NULL COMMENT 'user主键id',
  `system_tag` VARCHAR(1) NOT NULL DEFAULT '1' COMMENT '1 系统内置 ,2 用户新增',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='数据源配置表';

CREATE TABLE t_topic_historical (
   `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
   `topicName` VARCHAR(128) NOT NULL COMMENT '主题名称',
   `groupId` VARCHAR(64) NOT NULL COMMENT '群组id',
   `eventId` VARCHAR(64)  NOT NULL COMMENT '事件id',
   `brokerId` VARCHAR(64) NOT  NULL COMMENT 'broker主键id',
   `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
   `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='主题历史数据表';



CREATE TABLE t_timer_scheduler (
   `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键id',
   `broker_id` INT(11) DEFAULT NULL COMMENT 'broker主键id',
   `user_id` INT(11) DEFAULT NULL COMMENT 'user主键id',
   `scheduler_name` VARCHAR(64) NOT NULL COMMENT '调度任务名称',
   `rule_database_id` INT(11) NULL DEFAULT NULL COMMENT '数据源 主键id',
   `period_params` VARCHAR(64) NOT  NULL COMMENT '任务周期参数',
   `parsing_sql` VARCHAR(1024) NOT  NULL COMMENT '任务解析sql',
   `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
   `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='定时任务表';


ALTER TABLE t_broker ADD CONSTRAINT brokerUrlDeleteAt UNIQUE (broker_url, delete_at) ;
ALTER TABLE t_rule_engine ADD CONSTRAINT ruleNameDeleteAt UNIQUE (rule_name, delete_at);
ALTER TABLE t_topic ADD CONSTRAINT topicNameBrokerGroupDelete UNIQUE (topic_name,broker_id,group_id,delete_at);
ALTER TABLE t_file_transport_channel ADD CONSTRAINT topicBrokerGroupDelete UNIQUE (topic_name,broker_id,group_id);
ALTER TABLE t_topic_historical ADD CONSTRAINT brokerIdGroupIdEventId UNIQUE (brokerId, groupId, eventId);
ALTER TABLE t_file_transport_status ADD CONSTRAINT topicBrokerGroupFileName UNIQUE (topic_name,broker_id,group_id,file_name);