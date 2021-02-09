package com.webank.weevent.governance.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;

import com.webank.weevent.governance.entity.base.TopicBase;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * FileTransportChannelEntity class.
 *
 * @author v_wbhwliu
 * @version 1.3
 * @since 2020/5/20
 */
@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_file_transport_channel",
        uniqueConstraints = {@UniqueConstraint(name = "topicBrokerGroupDelete", columnNames = {"topic_name", "broker_id", "group_id", "node_address", "role"})})
public class FileTransportChannelEntity extends TopicBase {

    @Column(name = "role", columnDefinition = "varchar(1)")
    private String role;

    @Column(name = "public_key", columnDefinition = "text")
    private String publicKey;

    @Column(name = "private_key", columnDefinition = "text")
    private String privateKey;

    @Column(name = "over_write", columnDefinition = "varchar(1)")
    private String overWrite;
    
    @Column(name = "node_address", columnDefinition = "varchar(64)")
    private String nodeAddress;

    @Transient
    private String createTime;

    @Transient
    private boolean isVerified;


}
