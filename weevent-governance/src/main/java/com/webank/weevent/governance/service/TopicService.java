package com.webank.weevent.governance.service;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.webank.weevent.governance.code.ErrorCode;
import com.webank.weevent.governance.entity.BrokerEntity;
import com.webank.weevent.governance.entity.TopicEntity;
import com.webank.weevent.governance.entity.TopicPage;
import com.webank.weevent.governance.exception.GovernanceException;
import com.webank.weevent.governance.mapper.TopicInfoMapper;
import com.webank.weevent.governance.properties.ConstantProperties;
import com.webank.weevent.governance.result.GovernanceResult;
import com.webank.weevent.governance.utils.CookiesTools;
import com.webank.weevent.governance.utils.SpringContextUtil;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
    private TopicInfoMapper topicInfoMapper;

    @Autowired
    private BrokerService brokerService;

    @Autowired
    private CookiesTools cookiesTools;

    private final String SPLIT = "-";

    private final String HTTPS = "https";
    private final String HTTPS_CLIENT = "httpsClient";

    private final String HTTP_CLIENT = "httpClient";

    public Boolean close(Integer brokerId, String topic, HttpServletRequest request, HttpServletResponse response)
            throws GovernanceException {
        String accountId = cookiesTools.getCookieValueByName(request, ConstantProperties.COOKIE_MGR_ACCOUNT_ID);
        BrokerEntity brokerEntity = brokerService.getBroker(brokerId);
        if (brokerEntity == null) {
            return false;
        }
        if (!accountId.equals(brokerEntity.getUserId().toString())) {
            throw new GovernanceException(ErrorCode.ACCESS_DENIED);
        }
        CloseableHttpClient client = generateHttpClient(brokerEntity.getBrokerUrl());
        String url = new StringBuffer(brokerEntity.getBrokerUrl()).append("/rest/close?topic=").append(topic).toString();
        log.info("url: " + url);
        HttpGet get = getMethod(url, request);

        try {
            CloseableHttpResponse closeResponse = client.execute(get);
            String mes = EntityUtils.toString(closeResponse.getEntity());
            return (Boolean) JSON.parse(mes);
        } catch (Exception e) {
            log.error("close topic fail,topic :{},error:{}", topic, e.getMessage());
            throw new GovernanceException(ErrorCode.BROKER_CONNECT_ERROR);
        }
    }

    public TopicPage getTopics(Integer brokerId, Integer pageIndex, Integer pageSize, HttpServletRequest request,
                               HttpServletResponse response) throws GovernanceException {
        String accountId = cookiesTools.getCookieValueByName(request, ConstantProperties.COOKIE_MGR_ACCOUNT_ID);
        BrokerEntity brokerEntity = brokerService.getBroker(brokerId);
        if (!accountId.equals(brokerEntity.getUserId().toString())) {
            throw new GovernanceException(ErrorCode.ACCESS_DENIED);
        }
        TopicPage result = new TopicPage();
        result.setPageIndex(pageIndex);
        result.setPageSize(pageSize);
        if (brokerEntity == null) {
            return result;
        }
        // get event broker url
        CloseableHttpClient client = generateHttpClient(brokerEntity.getBrokerUrl());
        String url = new StringBuffer(brokerEntity.getBrokerUrl()).append("/rest/list")
                .append("?pageIndex=").append(pageIndex).append("&pageSize=").append(pageSize).toString();
        log.info("url: " + url);
        HttpGet get = getMethod(url, request);
        try {
            CloseableHttpResponse closeResponse = client.execute(get);
            String mes = EntityUtils.toString(closeResponse.getEntity());
            JSON json = JSON.parseObject(mes);
            result = JSON.toJavaObject(json, TopicPage.class);
            if (result == null || CollectionUtils.isEmpty(result.getTopicInfoList())) {
                return result;
            }
            //get creator
            List<TopicEntity> topicEntityList = result.getTopicInfoList();
            List<String> topicNameList = new ArrayList<>();
            topicEntityList.forEach(it -> {
                topicNameList.add(it.getTopicName());
            });
            List<TopicEntity> topicEntities = topicInfoMapper.getCreator(brokerId, topicNameList);
            if (CollectionUtils.isEmpty(topicEntities)) {
                return result;
            }
            Map<String, String> creatorMap = new HashMap<>();
            topicEntities.forEach(it -> {
                creatorMap.put(getKey(brokerId, it.getTopicName()), it.getCreater());
            });
            // set creator
            topicEntityList.forEach(it -> {
                it.setCreater(creatorMap.get(getKey(brokerId, it.getTopicName())));
            });
            result.setTopicInfoList(topicEntityList);
            return result;
        } catch (Exception e) {
            log.error("get topics fail,brokerId :{},error:{}", brokerId, e.getMessage());
            throw new GovernanceException(ErrorCode.BROKER_CONNECT_ERROR);
        }
    }

    public TopicEntity getTopicInfo(Integer brokerId, String topic, String groupId, HttpServletRequest request) throws GovernanceException {
        String accountId = this.cookiesTools.getCookieValueByName(request, ConstantProperties.COOKIE_MGR_ACCOUNT_ID);
        BrokerEntity broker = this.brokerService.getBroker(brokerId);
        if (StringUtils.isBlank(accountId) || broker == null || !accountId.equals(String.valueOf(broker.getUserId()))) {
            log.error("get topicInfo failed, brokerId:{}, topic:{}, groupId:{}.", brokerId, topic, groupId);
            throw new GovernanceException(ErrorCode.ACCESS_DENIED);
        }

        CloseableHttpClient client = generateHttpClient(broker.getBrokerUrl());
        // get event broker url
        String url = new StringBuffer(broker.getBrokerUrl()).append("/rest/state").append("?topic=")
                .append(topic).toString();
        if (!StringUtils.isBlank(groupId)) {
            url = new StringBuffer(url).append("&groupId=").append(groupId).toString();
        }

        log.info("getTopicInfo url: " + url);
        HttpGet get = getMethod(url, request);

        try {
            CloseableHttpResponse closeResponse = client.execute(get);
            String mes = EntityUtils.toString(closeResponse.getEntity());
            JSON json = JSON.parseObject(mes);
            TopicEntity result = JSON.toJavaObject(json, TopicEntity.class);

            if (result != null) {
                // get creator from database
                List<TopicEntity> creators = this.topicInfoMapper.getCreator(brokerId, new ArrayList<>(Arrays.asList(topic)));
                if (CollectionUtils.isNotEmpty(creators)) {
                    result.setCreater(creators.get(0).getCreater());
                }
                return result;
            }
        } catch (Exception e) {
            log.error("get topicInfo failed, error:{}", e.getMessage());
            throw new GovernanceException(ErrorCode.BROKER_CONNECT_ERROR);
        }
        return null;
    }

    @Transactional(rollbackFor = Throwable.class)
    public GovernanceResult open(Integer brokerId, String topic, String creater, HttpServletRequest request,
                                 HttpServletResponse response) throws GovernanceException {
        String accountId = cookiesTools.getCookieValueByName(request, ConstantProperties.COOKIE_MGR_ACCOUNT_ID);
        BrokerEntity brokerEntity = brokerService.getBroker(brokerId);
        if (brokerEntity == null) {
            return null;
        }
        if (!accountId.equals(brokerEntity.getUserId().toString())) {
            throw new GovernanceException(ErrorCode.ACCESS_DENIED);
        }
        TopicEntity topicEntity = new TopicEntity();
        topicEntity.setBrokerId(brokerId);
        topicEntity.setTopicName(topic);
        topicEntity.setCreater(creater);
        topicInfoMapper.openBrokeTopic(topicEntity);

        CloseableHttpClient client = generateHttpClient(brokerEntity.getBrokerUrl());
        String url = new StringBuffer(brokerEntity.getBrokerUrl()).append("/rest/open?topic=").append(topic).toString();
        log.info("url: " + url);
        HttpGet get = getMethod(url, request);
        String mes;
        try {
            CloseableHttpResponse closeResponse = client.execute(get);
            mes = EntityUtils.toString(closeResponse.getEntity());
        } catch (Exception e) {
            log.error("broker connect error,error:{}", e.getMessage());
            throw new GovernanceException(ErrorCode.BROKER_CONNECT_ERROR);
        }
        try {
            Boolean result = (Boolean) JSON.parse(mes);
            return new GovernanceResult(result);
        } catch (Exception e) {
            log.error("parse json fail,error:{}", e.getMessage());
            JSON json = JSON.parseObject(mes);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JSON.toJavaObject(json, Map.class);
            throw new GovernanceException((Integer) (result.get("code")), result.get("message").toString());
        }
    }

    // generate CloseableHttpClient from url
    private CloseableHttpClient generateHttpClient(String url) {
        if (url.startsWith(HTTPS)) {
            CloseableHttpClient bean = (CloseableHttpClient) SpringContextUtil.getBean(HTTPS_CLIENT);
            return bean;
        } else {
            CloseableHttpClient bean = (CloseableHttpClient) SpringContextUtil.getBean(HTTP_CLIENT);
            return bean;
        }
    }

    private HttpGet getMethod(String uri, HttpServletRequest request) throws GovernanceException {
        try {
            URIBuilder builder = new URIBuilder(uri);
            Enumeration<String> enumeration = request.getParameterNames();
            while (enumeration.hasMoreElements()) {
                String nex = enumeration.nextElement();
                builder.setParameter(nex, request.getParameter(nex));
            }
            return new HttpGet(builder.build());
        } catch (URISyntaxException e) {
            log.error("test url fail,error:{}", e.getMessage());
            throw new GovernanceException(ErrorCode.TEST_URL_FAIL);
        }
    }

    private String getKey(Integer brokerId, String topicName) {
        return brokerId + SPLIT + topicName;
    }

}
