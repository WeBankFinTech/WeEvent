package com.webank.weevent.broker.entiry.base;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.MappedSuperclass;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenerationTime;

/**
 * BaseEntity class
 *
 * @since 2019/10/15
 */
@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
@MappedSuperclass
public class BaseEntity {

    /**
     * primary key
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "create_date", insertable = false, updatable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    @org.hibernate.annotations.Generated(value = GenerationTime.INSERT)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createDate;

    @Column(name = "last_update", insertable = false, updatable = false, columnDefinition = "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP")
    @org.hibernate.annotations.Generated(value = GenerationTime.ALWAYS)
    @Temporal(TemporalType.TIMESTAMP)
    private Date lastUpdate;


}