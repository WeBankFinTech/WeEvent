package com.webank.weevent.broker.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.webank.weevent.broker.entiry.AccountEntity;

@Repository
public interface AccountRepository extends JpaRepository<AccountEntity, Long> {

    AccountEntity findAllByUserNameAndDeleteAt(String userName, Long deleteAt);
    
}
