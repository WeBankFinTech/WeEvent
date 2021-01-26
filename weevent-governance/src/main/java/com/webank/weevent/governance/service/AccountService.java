package com.webank.weevent.governance.service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;

import com.webank.weevent.governance.common.GovernanceConfig;
import com.webank.weevent.governance.common.GovernanceException;
import com.webank.weevent.governance.common.GovernanceResult;
import com.webank.weevent.governance.entity.AccountEntity;
import com.webank.weevent.governance.enums.IsDeleteEnum;
import com.webank.weevent.governance.repository.AccountRepository;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Slf4j
public class AccountService {

    @Autowired
    private AccountRepository accountRepository;

    @PostConstruct
    public void init() throws GovernanceException {
        try {
            // check database contain admin
            AccountEntity accountEntity = this.queryByUsername("admin");
            if (accountEntity == null) {
                accountEntity = new AccountEntity();
                accountEntity.setUsername(GovernanceConfig.acount_name);
                accountEntity.setPassword(GovernanceConfig.acount_passwrod);
                accountRepository.save(accountEntity);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new GovernanceException("init admin account fail,error:{}", e);
        }
    }

    public GovernanceResult<Object> checkData(String param, int type) {
        // according type generate select condition
        AccountEntity accountEntity = new AccountEntity();
        // 1：username
        if (type == 1) {
            accountEntity.setUsername(param);
        } else {
            return GovernanceResult.build(400, "data type error");
        }
        // excute select

        List<AccountEntity> list = this.findAllByUsernameAndDeleteAt(accountEntity.getUsername());
        // is list contain data
        if (!CollectionUtils.isEmpty(list)) {
            // if list contain data return false
            return GovernanceResult.ok(false);
        }
        // if not contain data true
        return GovernanceResult.ok(true);
    }

    public GovernanceResult<Object> register(AccountEntity user) throws GovernanceException {
        // data criteral
        if (StringUtils.isBlank(user.getUsername()) || StringUtils.isBlank(user.getPassword())) {
            return GovernanceResult.build(400, "user data incomplete，register fail");
        }
        // check username exist
        GovernanceResult result = checkData(user.getUsername(), 1);
        if (!(boolean) result.getData()) {
            return GovernanceResult.build(400, "this username occupied");
        }

        if (user.getPassword().length() < 6) {
            return GovernanceResult.build(400, "password is too short");
        }

        // secret
        // insert user into database
        accountRepository.save(user);
        // return true
        return GovernanceResult.ok();
    }

    public GovernanceResult<Object> updatePassword(AccountEntity user) {
        // data criteral
        if (StringUtils.isBlank(user.getPassword()) || StringUtils.isBlank(user.getOldPassword())) {
            return GovernanceResult.build(400, "password is blank，update fail");
        }

        if (user.getPassword().length() < 6) {
            return GovernanceResult.build(400, "password is too short");
        }
        // check oldPassword is correct
        String oldPassword = user.getOldPassword();

        List<AccountEntity> accountEntityList = this.findAllByUsernameAndDeleteAt(user.getUsername());
        if (CollectionUtils.isEmpty(accountEntityList)) {
            return GovernanceResult.build(400, "username is not exist");
        }
        AccountEntity storeUser = accountEntityList.get(0);
        if (!oldPassword.equals(storeUser.getPassword())) {
            return GovernanceResult.build(400, "old password is incorrect");
        }

        storeUser.setPassword(user.getPassword());
        accountRepository.save(storeUser);
        return GovernanceResult.ok();
    }

    public GovernanceResult<Object> getUserId(String username) {
        // get user by username
        AccountEntity user = this.queryByUsername(username);
        Integer userId = user == null ? null : user.getId();
        return GovernanceResult.ok(userId);
    }

    public GovernanceResult<Object> resetPassword(AccountEntity user) {
        if (user.getPassword().length() < 6) {
            return GovernanceResult.build(400, "password is too short");
        }

        AccountEntity storeUser = this.queryByUsername(user.getUsername());

        //     String password = passwordEncoder.encode(user.getPassword());
        //   storeUser.setPassword(password);
        storeUser.setLastUpdate(new Date());
        accountRepository.save(storeUser);
        return GovernanceResult.ok(true);
    }


    public AccountEntity queryByUsername(String username) {
        // execute select
        List<AccountEntity> list = this.findAllByUsernameAndDeleteAt(username);
        if (!list.isEmpty()) {
            // get user info
            return list.get(0);
        }
        return null;
    }

    public List<AccountEntity> accountEntityList(HttpServletRequest request, AccountEntity accountEntity, String accountId) {
        // execute select
        Example<AccountEntity> entityExample = Example.of(accountEntity);
        List<AccountEntity> list = accountRepository.findAll(entityExample);
        //filter current user
        list = list.stream().filter(it -> !it.getId().toString().equals(accountId)).collect(Collectors.toList());
        list.forEach(it -> it.setPassword(null));
        return list;
    }

    public void deleteUser(HttpServletRequest request, AccountEntity accountEntity) {
        // execute select
        accountRepository.deleteByUserName(accountEntity.getUsername(), new Date().getTime());
    }

    private List<AccountEntity> findAllByUsernameAndDeleteAt(String userName) {
        return accountRepository.findAllByUsernameAndDeleteAt(userName, IsDeleteEnum.NOT_DELETED.getCode());
    }

}