package com.webank.weevent.broker.entiry;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import javax.validation.constraints.NotBlank;

import com.webank.weevent.broker.entiry.base.BaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AccountBase class
 *
 * @since 2019/10/15
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_account_topic_auth", uniqueConstraints = { @UniqueConstraint(name = "userNameTopicName", columnNames = { "user_name", "topic_name" }) })
public class AccountTopicAuthEntity extends BaseEntity {

    @NotBlank
    @Column(name = "user_name")
    private String userName;

    @Column(name = "topic_name")
    private String topicName;
    
    @Column(name = "permission")
    private Integer permission;

    //0 means not deleted ,others means deleted
    @Column(name = "delete_at", nullable = false, columnDefinition = "BIGINT(16)")
    private Long deleteAt = 0L;

    public AccountTopicAuthEntity(@NotBlank String userName, @NotBlank String topicName) {
    	this.userName = userName;
        this.topicName = topicName;
    }

    public AccountTopicAuthEntity() {
    	
    }
}
