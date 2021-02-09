package com.webank.weevent.governance.service;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.webank.weevent.client.BaseResponse;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.JsonHelper;
import com.webank.weevent.governance.common.ConstantProperties;
import com.webank.weevent.governance.common.ErrorCode;
import com.webank.weevent.governance.common.GovernanceConfig;
import com.webank.weevent.governance.common.GovernanceException;
import com.webank.weevent.governance.common.GovernanceResult;
import com.webank.weevent.governance.entity.BrokerEntity;
import com.webank.weevent.governance.entity.TopicEntity;
import com.webank.weevent.governance.entity.TopicPage;
import com.webank.weevent.governance.entity.TopicPageEntity;
import com.webank.weevent.governance.enums.IsDeleteEnum;
import com.webank.weevent.governance.repository.TopicRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * topic service
 *
 * @since 2018/12/18
 */
@Service
@Slf4j
public class TopicService {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private BrokerService brokerService;

    @Autowired
    private CommonService commonService;

    private final String SPLIT = "-";
    
    public static GovernanceConfig governanceConfig;

    @Autowired
    public void setGovernanceConfig(GovernanceConfig config) {
        governanceConfig = config;
    }

    public Boolean close(Integer brokerId, String topic, String groupId, HttpServletRequest request, HttpServletResponse response)
            throws GovernanceException {
        BrokerEntity brokerEntity = brokerService.getBroker(brokerId);
        if (brokerEntity == null) {
            return false;
        }
        String url;
        try {
            if (groupId == null) {
                url = new StringBuffer(brokerEntity.getBrokerUrl()).append(ConstantProperties.BROKER_REST_CLOSE).append("?topic=").append(URLEncoder.encode(topic, "UTF-8")).
                        toString();
            } else {
                url = new StringBuffer(brokerEntity.getBrokerUrl()).append(ConstantProperties.BROKER_REST_CLOSE).append("?topic=").append(URLEncoder.encode(topic, "UTF-8"))
                        .append("&groupId=").append(groupId).toString();
            }
        } catch (Exception e) {
            log.error("close topic fail,topic :{},error:{}", topic, e.getMessage());
            throw new GovernanceException("close topic fail", e);
        }

        log.info("url:{}", url);

        return invokeBrokerCGI(request, url, new TypeReference<BaseResponse<Boolean>>() {
        }).getData();
    }

    public TopicPage getTopics(TopicPageEntity topicPageEntity, HttpServletRequest request,
                               HttpServletResponse response) throws GovernanceException {
        Integer brokerId = topicPageEntity.getBrokerId();
        Integer pageIndex = topicPageEntity.getPageIndex();
        Integer pageSize = topicPageEntity.getPageSize();
        String groupId = topicPageEntity.getGroupId();
        BrokerEntity brokerEntity = brokerService.getBroker(brokerId);
        TopicPage result = new TopicPage();
        result.setPageIndex(pageIndex);
        result.setPageSize(pageSize);
        result.setNodeAddress(governanceConfig.getNodes());
        if (brokerEntity == null) {
            return result;
        }
        // get event broker url
        String url;
        if (groupId != null) {
            url = new StringBuffer(brokerEntity.getBrokerUrl()).append(ConstantProperties.BROKER_REST_LIST)
                    .append("?pageIndex=").append(pageIndex).append("&pageSize=").append(pageSize)
                    .append("&groupId=").append(groupId).toString();
        } else {
            url = new StringBuffer(brokerEntity.getBrokerUrl()).append(ConstantProperties.BROKER_REST_LIST)
                    .append("?pageIndex=").append(pageIndex).append("&pageSize=").append(pageSize).toString();
        }
        log.info("url: {}", url);

        TopicPage topicPage = invokeBrokerCGI(request, url, new TypeReference<BaseResponse<TopicPage>>() {
        }).getData();

        topicPage.setNodeAddress(governanceConfig.getNodes());
        if (topicPage == null || CollectionUtils.isEmpty(topicPage.getTopicInfoList())) {
            return result;
        }
        //get creator
        List<TopicEntity> topicEntityList = topicPage.getTopicInfoList();
        List<String> topicNameList = new ArrayList<>();
        topicEntityList.forEach(it -> topicNameList.add(it.getTopicName()));
        List<TopicEntity> topicEntities = topicRepository.findAllByBrokerIdAndGroupIdAndTopicNameInAndDeleteAt(brokerId, groupId, topicNameList, IsDeleteEnum.NOT_DELETED.getCode());
        if (CollectionUtils.isEmpty(topicEntities)) {
            return topicPage;
        }
        Map<String, String> creatorMap = new HashMap<>();
        topicEntities.forEach(it -> creatorMap.put(getKey(brokerId, groupId, it.getTopicName()), it.getCreater()));
        // set creator
        topicEntityList.forEach(it -> it.setCreater(creatorMap.get(getKey(brokerId, groupId, it.getTopicName()))));
        topicPage.setTopicInfoList(topicEntityList);

        return topicPage;
    }

    public TopicEntity getTopicInfo(Integer brokerId, String topic, String groupId, HttpServletRequest request) throws GovernanceException {
        BrokerEntity broker = this.brokerService.getBroker(brokerId);
        if (broker == null) {
            log.error("get topicInfo failed, brokerId:{}, topic:{}, groupId:{}.", brokerId, topic, groupId);
            throw new GovernanceException("broker is not exists");
        }
        String url;
        try {
            // get event broker url
            url = new StringBuffer(broker.getBrokerUrl()).append(ConstantProperties.BROKER_REST_STATE).append("?topic=")
                    .append(URLEncoder.encode(topic, "UTF-8")).toString();
            if (!StringUtils.isBlank(groupId)) {
                url = new StringBuffer(url).append("&groupId=").append(groupId).toString();
            }
        } catch (Exception e) {
            log.error("get topicInfo failed, error:{}", e.getMessage());
            throw new GovernanceException(ErrorCode.BROKER_CONNECT_ERROR);
        }
        log.info("getTopicInfo url:{}", url);

        TopicEntity topicEntity = invokeBrokerCGI(request, url, new TypeReference<BaseResponse<TopicEntity>>() {
        }).getData();

        if (topicEntity != null) {
            // get creator from database
            List<TopicEntity> creators = topicRepository.findAllByBrokerIdAndGroupIdAndTopicNameInAndDeleteAt(brokerId, groupId, new ArrayList<>(Collections.singletonList(topic)), IsDeleteEnum.NOT_DELETED.getCode());
            if (CollectionUtils.isNotEmpty(creators)) {
                topicEntity.setCreater(creators.get(0).getCreater());
            }
        }

        return topicEntity;
    }

    @Transactional(rollbackFor = Throwable.class)
    public GovernanceResult<Object> open(Integer brokerId, String topic, String creater, String groupId, HttpServletRequest request,
                                         HttpServletResponse response) throws GovernanceException {
        BrokerEntity brokerEntity = brokerService.getBroker(brokerId);
        if (brokerEntity == null) {
            return null;
        }
        String url;
        try {
            boolean exist = exist(topic, brokerEntity.getBrokerUrl(), groupId, request);
            if (exist) {
                log.info("topic already exists,topic{}", topic);
                return new GovernanceResult<>(ErrorCode.TOPIC_EXISTS);
            }
            TopicEntity topicEntity = new TopicEntity();
            topicEntity.setBrokerId(brokerId);
            topicEntity.setTopicName(topic);
            topicEntity.setCreater(creater);
            topicEntity.setGroupId(groupId);
            topicRepository.save(topicEntity);

            if (groupId != null) {
                url = new StringBuffer(brokerEntity.getBrokerUrl()).append(ConstantProperties.BROKER_REST_OPEN).append("?topic=").append(URLEncoder.encode(topic, "UTF-8"))
                        .append("&groupId=").append(groupId).toString();
            } else {
                url = new StringBuffer(brokerEntity.getBrokerUrl()).append(ConstantProperties.BROKER_REST_OPEN).append("?topic=").append(URLEncoder.encode(topic, "UTF-8")).toString();
            }
        } catch (Exception e) {
            log.error("open topic fail,error:{}", e.getMessage());
            throw new GovernanceException("open topic fail,error", e);
        }
        log.info("url: {}", url);

        return new GovernanceResult<>(invokeBrokerCGI(request, url, new TypeReference<BaseResponse<Boolean>>() {
        }).getData());

    }

    @Transactional(rollbackFor = Throwable.class)
    public boolean exist(String topic, String brokerUrl, String groupId, HttpServletRequest request) throws GovernanceException, IOException {
        String url = "";
        if (groupId == null) {
            url = new StringBuffer(brokerUrl).append(ConstantProperties.BROKER_REST_EXIST).append("?topic=").append(URLEncoder.encode(topic, "UTF-8"))
                    .toString();
        } else {
            url = new StringBuffer(brokerUrl).append(ConstantProperties.BROKER_REST_EXIST).append("?topic=").append(URLEncoder.encode(topic, "UTF-8"))
                    .append("&groupId=").append(groupId).toString();
        }

        log.info("url: {}", url);

        return invokeBrokerCGI(request, url, new TypeReference<BaseResponse<Boolean>>() {
        }).getData();
    }


    private String getKey(Integer brokerId, String groupId, String topicName) {
        return new StringBuilder(brokerId).append(SPLIT).append(topicName).append(SPLIT).append(groupId).toString();
    }

    private <T> BaseResponse<T> invokeBrokerCGI(HttpServletRequest request, String url, TypeReference<BaseResponse<T>> typeReference) throws GovernanceException {
        CloseableHttpClient httpClient = commonService.generateHttpClient();
        HttpGet get = commonService.getMethod(url, request);

        if (StringUtils.isBlank(url)) {
            log.error("invokeBrokerCGI failed, request url is null");
            throw new GovernanceException("request url is null");
        }

        long requestStartTime = System.currentTimeMillis();
        try (CloseableHttpResponse httpResponse = httpClient.execute(get)) {
            log.info("invokeBrokerCGI {} in {} millisecond, response:{}", url,
                    System.currentTimeMillis() - requestStartTime, httpResponse.getStatusLine().toString());
            if (HttpStatus.OK.value() != httpResponse.getStatusLine().getStatusCode()
                    || null == httpResponse.getEntity()) {
                log.error("invokeBrokerCGI failed, request url:{}, msg:{}", url, httpResponse.getStatusLine().toString());
                throw new GovernanceException("invokeBrokerCGI failed");
            }

            byte[] responseResult = EntityUtils.toByteArray(httpResponse.getEntity());
            BaseResponse<T> baseResponse = JsonHelper.json2Object(responseResult, typeReference);

            if (ErrorCode.SUCCESS.getCode() != baseResponse.getCode()) {
                log.error("invokeBrokerCGI failed, request url:{}, msg:{}", url, baseResponse.getMessage());
                throw new GovernanceException(baseResponse.getCode(), baseResponse.getMessage());
            }

            return baseResponse;
        } catch (IOException | BrokerException e) {
            log.error("invokeBrokerCGI error, request url:{}", url, e);
            throw new GovernanceException(ErrorCode.HTTP_REQUEST_EXECUTE_ERROR);
        }
    }

}
