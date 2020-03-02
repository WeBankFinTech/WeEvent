package com.webank.weevent.governance.entity;

import com.webank.weevent.governance.entity.base.TopicHistoricalBase;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.persistence.UniqueConstraint;
import java.util.Date;
import java.util.List;


@Setter
@Getter
@EqualsAndHashCode(callSuper = false)
@Entity
@Table(name = "t_topic_historical",
        uniqueConstraints = {@UniqueConstraint(name = "brokerIdGroupIdEventId",
                columnNames = {"brokerId", "groupId", "eventId"})})
public class TopicHistoricalEntity extends TopicHistoricalBase {

    @Transient
    private Integer eventCount;

    @Transient
    private Date beginDate;

    @Transient
    private Date endDate;

    @Transient
    private String beginDateStr;

    @Transient
    private String endDateStr;

    @Transient
    private String createDateStr;

    @Transient
    private List<String> topicList;

    @Transient
    private String tableName;


}
