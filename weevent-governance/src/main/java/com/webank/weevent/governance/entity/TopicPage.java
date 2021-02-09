package com.webank.weevent.governance.entity;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * TopicEntity data in page list.
 *
 * @since 2019/02/11
 */
@Setter
@Getter
public class TopicPage {

    private Integer total;

    private Integer pageIndex;

    private Integer pageSize;

    private List<TopicEntity> topicInfoList;
    
    private List<String> nodeAddress;
    
}
