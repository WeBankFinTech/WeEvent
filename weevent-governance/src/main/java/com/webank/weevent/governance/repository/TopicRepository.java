package com.webank.weevent.governance.repository;

import java.util.List;

import com.webank.weevent.governance.entity.TopicEntity;

import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TopicRepository extends JpaRepository<TopicEntity, Long> {

    //find topic by brokerId、groupId、topicNameList
    List<TopicEntity> findAllByBrokerIdAndGroupIdAndTopicNameInAndDeleteAt(Integer brokerId, String groupId, List<String> topicNameList, String deleteAt);

    //delete  topic by groupId and brokerId
    @Transactional
    @Modifying
    @Query(value = "update t_topic set delete_at=:deleteAt where topicName =:topicName and broker_id=:brokerId and group_id=:groupId")
    void deleteTopicInfo(@Param("topicName") String topicName, @Param("deleteAt") String deleteAt, @Param("brokerId") Integer brokerId, @Param("groupId") String groupId);

    //delete  topic by brokerId
    @Transactional
    @Modifying
    @Query(value = "update t_topic set delete_at=:deleteAt  where broker_id =:brokerId")
    void deleteByBrokerId(@Param("brokerId") Integer brokerId, @Param("deleteAt") String deleteAt);
}
