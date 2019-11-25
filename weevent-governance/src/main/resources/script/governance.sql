CREATE TABLE t_account(
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `delete_at`VARCHAR(64) NOT NULL DEFAULT  0 COMMENT ''0 means not deleted others means deleted'',
  `email` VARCHAR(256) NOT NULL COMMENT '邮箱',
  `username` VARCHAR(64) NOT NULL COMMENT '用户名',
  `password` VARCHAR(256) NOT NULL COMMENT '密码`',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment '用户表';


CREATE TABLE t_broker (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `delete_at` VARCHAR(64) NOT NULL DEFAULT  0 COMMENT ''0 means not deleted others means deleted'',
  `user_id` INT(11) NOT NULL  COMMENT 'user 主键id',
  `name` VARCHAR(256) NOT NULL COMMENT 'broker 名称',
  `broker_url` VARCHAR(256) DEFAULT NULL COMMENT 'broker url',
  `webase_url` VARCHAR(256) DEFAULT NULL COMMENT 'webase url',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment 'broker配置表';

CREATE TABLE  t_topic (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT 'primary key',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'create date',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment 'update date',
  `delete_at` VARCHAR(64) NOT NULL DEFAULT  0 COMMENT '0 means not deleted others means deleted',
  `topic_name` VARCHAR(256) NOT NULL COMMENT 'topic name',
  `creater` VARCHAR(256) DEFAULT NULL COMMENT 'creator',
  `broker_id` INT(11) NOT NULL COMMENT 'broker id',
  `group_id` VARCHAR(64) DEFAULT NULL COMMENT 'group id',
  `description` VARCHAR(256)  NULL  DEFAULT NULL COMMENT 'description',
   PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment '主题表';


CREATE TABLE t_permission (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `broker_id` INT(11) NOT NULL COMMENT 'broker 主键id',
  `user_id` INT(11) NOT NULL COMMENT 'user 主键id',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 comment 'broker授权表';

CREATE TABLE t_rule_engine (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `rule_name` VARCHAR(128) NOT  NULL   COMMENT '规则名称',
  `payload_type` INT(4)  NULL DEFAULT NULL COMMENT '1表示JASON',
  `payload` VARCHAR(4096)  NULL DEFAULT NULL COMMENT '规则描述',
  `broker_id` INT(11) not NULL COMMENT 'broker 主键id',
  `user_id` INT(11) not NULL COMMENT 'user 主键id',
  `group_id` VARCHAR(64) not NULL COMMENT '群组 id',
  `cep_id` VARCHAR(64) NULL COMMENT '规则id',
  `broker_url` VARCHAR(255) NULL DEFAULT NULL COMMENT 'broker url',
  `from_destination` VARCHAR(64)  NULL DEFAULT NULL COMMENT  '数据来源',
  `to_destination` VARCHAR(64)  NULL DEFAULT NULL COMMENT  '数据目的',
  `select_field` VARCHAR(4096) NULL DEFAULT NULL COMMENT '选择字段',
  `condition_type` INT(2) NULL DEFAULT NULL COMMENT '数据流转类型',
  `status` INT(2)  NULL DEFAULT null COMMENT '0 未启动, 1 运行,2 已经删除',
  `database_url` VARCHAR(255) NULL DEFAULT NULL COMMENT '数据库 url',
  `rule_database_id` INT(11) NULL DEFAULT NULL COMMENT '数据源 主键id',
  `error_destination` VARCHAR(255) NULL DEFAULT NULL COMMENT '失败流转目的地',
  `system_tag` VARCHAR(1) NOT NULL DEFAULT '1' COMMENT '1 系统内置 ,2 用户新增',
  `delete_at` VARCHAR(64) NOT NULL DEFAULT  '0' COMMENT '0 means not deleted ,others means deleted',
   PRIMARY KEY (`id`),
   UNIQUE KEY ruleName(rule_name)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8 COMMENT='规则引擎表';

CREATE TABLE t_rule_database (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `datasource_name` VARCHAR(256) NOT NULL COMMENT '数据源名称',
  `ip` VARCHAR(32) NOT NULL COMMENT '数据库ip',
  `port` VARCHAR(8) NOT NULL COMMENT '数据库端口',
  `database_name` VARCHAR(32) NOT NULL COMMENT '数据库名称',
  `username` VARCHAR(16) NOT NULL COMMENT '数据库用户名',
  `password` VARCHAR(128) NOT NULL COMMENT '数据库密码',
  `table_name` VARCHAR(32) NOT NULL COMMENT '表格名称',
  `optional_parameter` VARCHAR(256) DEFAULT NULL COMMENT '数据库可选参数',
  `broker_id` VARCHAR(256) DEFAULT NULL COMMENT 'broker 主键id',
  `user_id` VARCHAR(256) DEFAULT NULL COMMENT 'user 主键id',
  `system_tag` VARCHAR(1) NOT NULL DEFAULT '1' COMMENT '1 系统内置 ,2 用户新增',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='数据源配置表';

CREATE TABLE t_rule_engine_condition (
  `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
  `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
  `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
  `rule_id` INT(64) DEFAULT NULL COMMENT '规则 id',
  `sql_condition_json` VARCHAR(512) DEFAULT NULL COMMENT '命中条件',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='规则引擎条件表';


CREATE TABLE t_topic_historical (
   `id` INT(11) NOT NULL AUTO_INCREMENT COMMENT '主键 id',
   `create_date` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
   `last_update` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
   `topicName` VARCHAR(128) NOT NULL COMMENT '主题名称',
   `groupId` VARCHAR(64) NOT NULL COMMENT '群组 id',
   `eventId` VARCHAR(64)  NOT NULL COMMENT '事件 id',
   `brokerId` VARCHAR(64) NOT  NULL COMMENT 'broker 主键id',
   PRIMARY KEY (`id`),
   UNIQUE KEY brokerIdGroupIdEventId(brokerId,groupId,eventId)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COMMENT='主题历史数据表';
