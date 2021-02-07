package com.webank.weevent.governance.repository;

import java.util.List;

import com.webank.weevent.governance.entity.FileTransportStatusEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TransportStatusRepository extends JpaRepository<FileTransportStatusEntity, Long> {

	List<FileTransportStatusEntity> queryByBrokerIdAndGroupIdAndNodeAddressAndTopicName(Integer brokerId, String groupId,
                                                                          String topicName, String nodeAddress);

	FileTransportStatusEntity queryByBrokerIdAndGroupIdAndTopicNameAndFileName(Integer brokerId, String groupId, String topicName, String fileName);

	@Transactional
	@Modifying
	@Query(value = "update t_file_transport_status set transport_status=?1 where id =?2", nativeQuery = true)
	void updateTransportStatus(String status, Long id);

	@Transactional
	@Modifying
	@Query(value = "update t_file_transport_status set speed=?1 where id =?2", nativeQuery = true)
	void updateTransportSpeed(String speed, Long id);
	
}
