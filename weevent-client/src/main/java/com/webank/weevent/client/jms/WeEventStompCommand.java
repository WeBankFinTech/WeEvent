package com.webank.weevent.client.jms;


import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.jms.JMSException;

import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.WeEvent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.util.MimeType;

/**
 * Stomp command.
 *
 * @author matthewliu
 * @author cristicmei
 * @since 2019/04/11
 */
@Slf4j
@Getter
@Setter
public class WeEventStompCommand {
    private final static String stompVersion = "1.1";
    private final static int stompHeartBeat = 30;

    private String subscriptionId;
    private WeEventTopic topic;
    private WeEvent event;
    private String headerId;

    public WeEventStompCommand() {
    }

    public WeEventStompCommand(WeEvent event) {
        this.event = event;
    }

    public String encodeRaw(StompHeaderAccessor accessor) {
        return encodeRaw(accessor, null);
    }

    public String encodeRaw(StompHeaderAccessor accessor, byte[] payload) {
        log.debug("encode STOMP command: {}", accessor.getCommand());

        MessageHeaders headers = accessor.getMessageHeaders();
        Message<byte[]> message = MessageBuilder.createMessage(payload != null ? payload : "".getBytes(StandardCharsets.UTF_8), headers);
        return new String((new StompEncoder()).encode(message), StandardCharsets.UTF_8);
    }

    public String encodeConnect(String userName, String password) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setVersion(stompVersion);
        accessor.setAcceptVersion(stompVersion);
        accessor.setHeartbeat(stompHeartBeat, 0);

        if (!userName.isEmpty()) {
            accessor.setLogin(userName);
        }
        if (!password.isEmpty()) {
            accessor.setPasscode(password);
        }

        return encodeRaw(accessor);
    }

    public String encodeDisConnect(Long receiptId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.DISCONNECT);
        accessor.setReceipt(Long.toString(receiptId));
        return encodeRaw(accessor);
    }

    public String encodeSubscribe(WeEventTopic topic, Long id) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(topic.getTopicName());
        accessor.setNativeHeader("eventId", topic.getOffset());
        if (topic.isFile()) {
            accessor.setNativeHeader(WeEvent.WeEvent_FILE, "1");
        }
        accessor.setNativeHeader(StompHeaderAccessor.STOMP_ID_HEADER, Long.toString(id));
        if (!StringUtils.isBlank(topic.getGroupId())) {
            accessor.setNativeHeader("groupId", topic.getGroupId());
        }

        // keep the original SubscriptionId
        if (!StringUtils.isBlank(topic.getContinueSubscriptionId())) {
            accessor.setNativeHeader(WeEvent.WeEvent_SubscriptionId, topic.getContinueSubscriptionId());
        }
        return encodeRaw(accessor);
    }

    public String encodeUnSubscribe(String subscriptionId, String headerId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setNativeHeader(StompHeaderAccessor.SUBSCRIPTION_ID_HEADER, headerId);
        accessor.setNativeHeader(StompHeaderAccessor.STOMP_SUBSCRIPTION_HEADER, subscriptionId);
        accessor.setNativeHeader(StompHeaderAccessor.STOMP_ID_HEADER, headerId);
        return encodeRaw(accessor);
    }

    // payload is WeEvent
    public String encodeSend(Long id, WeEventTopic topic, WeEvent weEvent) throws JMSException {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination(topic.getTopicName());
        accessor.setContentType(new MimeType("text", "plain", StandardCharsets.UTF_8));
        accessor.setContentLength(weEvent.getContent().length);
        accessor.setReceipt(Long.toString(id));
        if (!StringUtils.isBlank(topic.getGroupId())) {
            accessor.setNativeHeader("groupId", topic.getGroupId());
        }
        for (Map.Entry<String, String> entry : weEvent.getExtensions().entrySet()) {
            accessor.setNativeHeader(entry.getKey(), entry.getValue());
        }

        return encodeRaw(accessor, weEvent.getContent());
    }

    public void checkError(StompHeaderAccessor stompHeaderAccessor) throws JMSException {
        if (StompCommand.ERROR == stompHeaderAccessor.getCommand()) {
            String message = stompHeaderAccessor.getMessage();
            log.error("message in ERROR frame, {}", message);
            if (!StringUtils.isEmpty(message)) {
                throw new JMSException(message);
            } else {
                throw WeEventConnectionFactory.error2JMSException(ErrorCode.SDK_JMS_EXCEPTION_STOMP_EXECUTE);
            }
        }
    }
}
