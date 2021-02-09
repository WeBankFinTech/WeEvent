package com.webank.weevent.broker.protocol.jsonrpc;

import java.util.Map;

import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.JsonHelper;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.TopicInfo;
import com.webank.weevent.client.TopicPage;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.client.jsonrpc.IBrokerRpc;

import com.googlecode.jsonrpc4j.JsonRpcParam;
import com.googlecode.jsonrpc4j.JsonRpcService;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Implement of JsonRpc service.
 *
 * @author matthewliu
 * @since 2018/11/21
 */
@Slf4j
@Component
@JsonRpcService("/jsonrpc")
@AutoJsonRpcServiceImpl
public class BrokerRpc implements IBrokerRpc {
    private FiscoConfig fiscoConfig;
    private IProducer producer;

    @Autowired
    public void setFiscoConfig(FiscoConfig fiscoConfig) {
        this.fiscoConfig = fiscoConfig;
    }

    @Autowired
    public void setProducer(IProducer producer) {
        this.producer = producer;
    }

    @Override
    public SendResult publish(@JsonRpcParam(value = "topic") String topic,
                              @JsonRpcParam(value = "groupId") String groupId,
                              @JsonRpcParam(value = "content") byte[] content,
                              @JsonRpcParam(value = "extensions") Map<String, String> extensions) throws BrokerException {
        log.info("topic:{} groupId:{} content.length:{} extensions:{}", topic, groupId, content.length, JsonHelper.object2Json(extensions));

        return this.producer.publish(new WeEvent(topic, content, extensions), groupId, this.fiscoConfig.getWeEventCoreConfig().getTimeout());
    }

    @Override
    public WeEvent getEvent(@JsonRpcParam(value = "eventId") String eventId,
                            @JsonRpcParam(value = "groupId") String groupId) throws BrokerException {
        log.info("eventId:{} groupId:{}", eventId, groupId);

        return this.producer.getEvent(eventId, groupId);
    }

    @Override
    public boolean open(@JsonRpcParam(value = "topic") String topic,
                        @JsonRpcParam(value = "groupId") String groupId) throws BrokerException {
        log.info("topic:{} groupId:{}", topic, groupId);

        return this.producer.open(topic, groupId);
    }

    @Override
    public boolean close(@JsonRpcParam(value = "topic") String topic,
                         @JsonRpcParam(value = "groupId") String groupId) throws BrokerException {
        log.info("topic:{} groupId:{}", topic, groupId);

        return this.producer.close(topic, groupId);
    }

    @Override
    public boolean exist(@JsonRpcParam(value = "topic") String topic,
                         @JsonRpcParam(value = "groupId") String groupId) throws BrokerException {
        log.info("topic:{} groupId:{}", topic, groupId);

        return this.producer.exist(topic, groupId);
    }

    @Override
    public TopicPage list(@JsonRpcParam(value = "pageIndex") Integer pageIndex,
                          @JsonRpcParam(value = "pageSize") Integer pageSize,
                          @JsonRpcParam(value = "groupId") String groupId) throws BrokerException {
        log.info("pageIndex:{} pageSize:{} groupId:{}", pageIndex, pageSize, groupId);

        return this.producer.list(pageIndex, pageSize, groupId);
    }

    @Override
    public TopicInfo state(@JsonRpcParam(value = "topic") String topic,
                           @JsonRpcParam(value = "groupId") String groupId) throws BrokerException {
        log.info("topic:{} groupId:{}", topic, groupId);

        return this.producer.state(topic, groupId);
    }
}
