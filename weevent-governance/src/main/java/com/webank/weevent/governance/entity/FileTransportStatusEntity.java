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
 * FileTransportStatusEntity class.
 *
 * @author v_wbhwliu
 * @version 1.3
 * @since 2020/5/20
 */
@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_file_transport_status",
        uniqueConstraints = {@UniqueConstraint(name = "topicBrokerGroupFileName",
                columnNames = {"broker_id", "group_id", "topic_name", "node_address", "file_name"})})
public class FileTransportStatusEntity extends TopicBase {

    @Column(name = "file_name", columnDefinition = "varchar(256)")
    private String fileName;

    @Column(name = "transport_status", columnDefinition = "varchar(1)")
    private String status;

    @Column(name = "file_md5", columnDefinition = "varchar(32)")
    private String fileMD5;

    @Column(name = "file_size")
    private Long fileSize;
    
    @Column(name = "speed", columnDefinition = "varchar(32)")
    private String speed;
    
    @Column(name = "node_address", columnDefinition = "varchar(64)")
    private String nodeAddress;

    // cost time in second
    @Transient
    private String time;
    // sender ready chunk
    @Transient
    private int readyChunk;
    // processing
    @Transient
    private String process;
    // speed in Byte/s
    
//    @Transient
//    private FileChunksMetaStatus fileChunksMetaStatus;
    
}
