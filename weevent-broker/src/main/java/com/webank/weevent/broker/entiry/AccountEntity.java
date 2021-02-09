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
@Table(name = "t_account", uniqueConstraints = {@UniqueConstraint(name = "userName", columnNames = {"user_name"})})
public class AccountEntity extends BaseEntity {

    @NotBlank
    @Column(name = "user_name")
    private String userName;

    @Column(name = "password")
    private String password;

    // 0 means not deleted ,others means deleted
    @Column(name = "delete_at", nullable = false, columnDefinition = "BIGINT(16)")
    private Long deleteAt = 0L;

    public AccountEntity(@NotBlank String userName) {
        this.userName = userName;
    }

    public AccountEntity() {

    }
}
