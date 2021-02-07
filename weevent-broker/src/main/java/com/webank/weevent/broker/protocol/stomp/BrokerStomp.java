package com.webank.weevent.broker.protocol.stomp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.webank.weevent.broker.config.WeEventConfig;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.fisco.constant.WeEventConstants;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.util.Pair;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompEncoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Stomp 1.1 protocol.
 * see at https://stomp.github.io/stomp-specification-1.1.html.
 * Support sockjs + stomp.js client.
 * see at https://github.com/sockjs/sockjs-client and https://github.com/stomp-js/stompjs.
 *
 * @author matthewliu
 * @since 2018/12/20
 */
@Slf4j
@Component
public class BrokerStomp extends TextWebSocketHandler {
    private WeEventConfig weEventConfig;
    private FiscoConfig fiscoConfig;
    private IProducer iproducer;
    private IConsumer iconsumer;

    private String authAccount = "";
    private String authPassword = "";

    // session id <-> (subscription id in stomp's header <-> (subscription id in consumer, topic))
    private static Map<String, Map<String, Pair<String, String>>> sessionContext = new HashMap<>();

    @Autowired
    public void setWeEventConfig(WeEventConfig weEventConfig) {
        this.weEventConfig = weEventConfig;
    }

    @Autowired
    public void setFiscoConfig(FiscoConfig fiscoConfig) {
        this.fiscoConfig = fiscoConfig;
    }

    @Autowired
    public void setProducer(IProducer producer) {
        this.iproducer = producer;
    }

    @Autowired
    public void setConsumer(IConsumer consumer) {
        this.iconsumer = consumer;
    }

    @Autowired
    public void setAuthAccount(Environment environment) {
        this.authAccount = environment.getProperty("spring.security.user.name");
        this.authPassword = environment.getProperty("spring.security.user.password");
    }

    private void handleSingleMessage(Message<byte[]> msg, WebSocketSession session) {
        StompHeaderAccessor stompHeaderAccessor = StompHeaderAccessor.wrap(msg);
        if (stompHeaderAccessor.isHeartbeat()) {
            log.debug("heart beat from client: {}", session.getId());
            return;
        }

        StompCommand stompCommand = stompHeaderAccessor.getCommand();
        if (stompCommand == null) {
            log.error("miss command in stomp header, close session");
            this.closeSession(session);
            return;
        }

        log.info("session id: {} stomp header: {} payload.length: {}", session.getId(), stompHeaderAccessor.getMessageHeaders(), msg.getPayload().length);
        switch (stompCommand) {
            case CONNECT:
                handleConnectMessage(stompHeaderAccessor, session);
                break;

            case DISCONNECT:
                handleDisconnectMessage(stompHeaderAccessor, session);
                break;

            case SEND:
                handleSendMessage(stompHeaderAccessor, msg, session);
                break;

            case SUBSCRIBE:
                handleSubscribeMessage(stompHeaderAccessor, session);
                break;

            case UNSUBSCRIBE:
                handleUnsubscribeMessage(stompHeaderAccessor, session);
                break;

            default:
                handleDefaultMessage(stompHeaderAccessor, session);
                break;
        }
    }

    private void handleConnectMessage(StompHeaderAccessor stompHeaderAccessor, WebSocketSession session) {
        boolean result = authorize(stompHeaderAccessor);

        // send response
        StompHeaderAccessor accessor;
        if (result) {
            accessor = StompHeaderAccessor.create(StompCommand.CONNECTED);
            accessor.setHeartbeat(0, this.weEventConfig.getStompHeartbeats() * 1000);
        } else {
            accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            accessor.setMessage("login or password is wrong");
        }
        accessor.setVersion("1.1");
        sendSimpleMessage(session, accessor);
    }

    private void handleDisconnectMessage(StompHeaderAccessor stompHeaderAccessor, WebSocketSession session) {
        // send response
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.RECEIPT);
        accessor.setReceiptId(stompHeaderAccessor.getReceipt());
        sendSimpleMessage(session, accessor);

        // close session after reply to client
        this.closeSession(session);
    }


    @SuppressWarnings("unchecked")
    public Map<String, String> getWeEventExtend(Message<byte[]> msg) {
        Map<String, String> extensions = new HashMap<>();

        Map<String, List<String>> nativeHeaders = ((Map<String, List<String>>) msg.getHeaders().get("nativeHeaders"));
        if (nativeHeaders == null) {
            log.error("miss nativeHeaders in stomp header");
            return extensions;
        }

        for (Map.Entry<String, List<String>> extension : nativeHeaders.entrySet()) {
            if (extension.getKey().startsWith(WeEventConstants.EXTENSIONS_PREFIX_CHAR)) {
                extensions.put(extension.getKey(), extension.getValue().get(0));
            }
        }
        return extensions;
    }

    private void handleSendMessage(StompHeaderAccessor stompHeaderAccessor, Message<byte[]> msg, WebSocketSession session) {
        Map<String, String> extensions = this.getWeEventExtend(msg);
        // group id
        String groupId = stompHeaderAccessor.getFirstNativeHeader(WeEventConstants.EVENT_GROUP_ID);
        if (StringUtils.isBlank(groupId)) {
            groupId = "";
        }

        try {
            String destination = stompHeaderAccessor.getDestination();

            // publish event
            SendResult sendResult = this.iproducer.publish(new WeEvent(destination, msg.getPayload(), extensions), groupId, this.fiscoConfig.getWeEventCoreConfig().getTimeout());

            // send response
            StompHeaderAccessor accessor;
            if (sendResult.getStatus().equals(SendResult.SendResultStatus.SUCCESS)) {
                accessor = StompHeaderAccessor.create(StompCommand.RECEIPT);
                accessor.setDestination(destination);
                accessor.setNativeHeader(WeEventConstants.EXTENSIONS_EVENT_ID, sendResult.getEventId());
            } else {
                accessor = StompHeaderAccessor.create(StompCommand.ERROR);
                accessor.setMessage(sendResult.toString());
            }
            accessor.setReceiptId(stompHeaderAccessor.getReceipt());

            sendSimpleMessage(session, accessor);
        } catch (BrokerException e) {
            handleErrorMessage(session, e, stompHeaderAccessor.getReceipt());
        }
    }

    private void handleSubscribeMessage(StompHeaderAccessor stompHeaderAccessor, WebSocketSession session) {
        // subscribe command id
        String headerIdStr = stompHeaderAccessor.getFirstNativeHeader(StompHeaderAccessor.STOMP_ID_HEADER);

        // group
        String groupId = stompHeaderAccessor.getFirstNativeHeader(WeEventConstants.EVENT_GROUP_ID);
        if (StringUtils.isBlank(groupId)) {
            groupId = "";
        }

        // check if there hasn't the event, need use the last id
        String subEventId = stompHeaderAccessor.getFirstNativeHeader(WeEventConstants.EXTENSIONS_EVENT_ID);
        if (StringUtils.isBlank(subEventId)) {
            subEventId = WeEvent.OFFSET_LAST;
        }

        // subscription id
        String continueSubscriptionIdStr = stompHeaderAccessor.getFirstNativeHeader(WeEvent.WeEvent_SubscriptionId);
        if (StringUtils.isBlank(continueSubscriptionIdStr)) {
            continueSubscriptionIdStr = "";
        }

        // tag
        String tag = stompHeaderAccessor.getFirstNativeHeader(WeEvent.WeEvent_TAG);
        if (StringUtils.isBlank(tag)) {
            tag = "";
        }

        // is ephemeral
        boolean ephemeral = stompHeaderAccessor.getFirstNativeHeader(WeEvent.WeEvent_EPHEMERAL) != null;

        try {
            String simpDestination = stompHeaderAccessor.getDestination();
            String subscriptionId = handleSubscribe(session,
                    simpDestination,
                    groupId,
                    headerIdStr,
                    subEventId,
                    continueSubscriptionIdStr,
                    tag,
                    ephemeral);

            // send response
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.RECEIPT);
            accessor.setDestination(simpDestination);
            // a unique identifier for that message and a subscription header matching the identifier of the subscription that is receiving the message.
            accessor.setReceiptId(headerIdStr);
            accessor.setSubscriptionId(subscriptionId);
            accessor.setNativeHeader("subscription-id", subscriptionId);
            sendSimpleMessage(session, accessor);
        } catch (BrokerException e) {
            handleErrorMessage(session, e, headerIdStr);
        }
    }

    private void handleUnsubscribeMessage(StompHeaderAccessor stompHeaderAccessor, WebSocketSession session) {
        String simpDestination = stompHeaderAccessor.getDestination();
        String headerIdStr = stompHeaderAccessor.getFirstNativeHeader(StompHeaderAccessor.STOMP_ID_HEADER);

        try {
            boolean result = handleUnSubscribe(session, headerIdStr);

            // send response
            StompHeaderAccessor accessor;
            if (result) {
                accessor = StompHeaderAccessor.create(StompCommand.RECEIPT);
                accessor.setDestination(simpDestination);
            } else {
                accessor = StompHeaderAccessor.create(StompCommand.ERROR);
            }
            // a unique identifier for that message and a subscription header matching the identifier of the subscription that is receiving the message.
            accessor.setReceiptId(headerIdStr);
            sendSimpleMessage(session, accessor);
        } catch (BrokerException e) {
            handleErrorMessage(session, e, headerIdStr);
        }
    }

    private void handleDefaultMessage(StompHeaderAccessor stompHeaderAccessor, WebSocketSession session) {
        String simpDestination = stompHeaderAccessor.getDestination();
        String headerIdStr = stompHeaderAccessor.getFirstNativeHeader(StompHeaderAccessor.STOMP_ID_HEADER);

        // send response
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);
        accessor.setDestination(simpDestination);
        accessor.setMessage("NOT SUPPORT COMMAND");
        accessor.setReceiptId(headerIdStr);
        // a unique identifier for that message and a subscription header matching the identifier of the subscription that is receiving the message.
        sendSimpleMessage(session, accessor);

        // follow protocol 1.2 to close connection
        this.closeSession(session);
    }

    private void closeSession(WebSocketSession session) {
        try {
            clearSession(session);
            session.close();
        } catch (Exception e) {
            log.error("exception in close session", e);
        }
    }

    // check login/password if needed
    private boolean authorize(StompHeaderAccessor stompHeaderAccessor) {
        if (StringUtils.isBlank(this.authAccount) || StringUtils.isBlank(this.authPassword)) {
            return true;
        }

        if (this.authAccount.equals(stompHeaderAccessor.getLogin())
                && this.authPassword.equals(stompHeaderAccessor.getPasscode())) {
            log.error("authorize success");
            return true;
        }

        log.error("authorize failed, check login/password in stomp header");
        return false;
    }

    private void clearSession(WebSocketSession session) {
        log.info("cleanup session: {}", session.getId());

        if (!sessionContext.containsKey(session.getId())) {
            log.error("not exist session: {}, skip it", session.getId());
            return;
        }

        // remove session id and subscriptions
        Map<String, Pair<String, String>> subscriptions = sessionContext.get(session.getId());
        sessionContext.remove(session.getId());

        log.info("find subscriptions: {}, try to IConsumer.unSubscribe one by one", subscriptions.size());
        for (Map.Entry<String, Pair<String, String>> subscription : subscriptions.entrySet()) {
            try {
                boolean result = this.iconsumer.unSubscribe(subscription.getValue().getFirst());
                log.info("IConsumer.unSubscribe result, {} <-> {}", subscription, result);
            } catch (BrokerException e) {
                log.error("exception in IConsumer.unSubscribe", e);
            }
        }
    }

    private void handleErrorMessage(WebSocketSession session, BrokerException e, String receiptId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.ERROR);

        // package the return frame ,include the error message and error code
        accessor.setMessage(e.getMessage());
        accessor.setNativeHeader("code", String.valueOf(e.getCode()));
        accessor.setReceiptId(receiptId);
        sendSimpleMessage(session, accessor);
    }

    private void send2Remote(WebSocketSession session, TextMessage textMessage) {
        try {
            if (!session.isOpen()) {
                log.warn("session is closed, skip sending to {}", session.getId());
                return;
            }

            log.info("send message to remote, {}", session.getId());
            session.sendMessage(textMessage);
        } catch (IOException e) {
            log.error("exception in send simple message to remote", e);
        }
    }

    private void sendSimpleMessage(WebSocketSession session, StompHeaderAccessor accessor) {
        MessageHeaders headers = accessor.getMessageHeaders();
        Message<byte[]> message1 = MessageBuilder.createMessage("".getBytes(StandardCharsets.UTF_8), headers);
        byte[] bytes = new StompEncoder().encode(message1);
        TextMessage textMessage = new TextMessage(bytes);
        send2Remote(session, textMessage);
    }

    /**
     * @param session stomp session
     * @param topic topic name
     * @param groupId group id
     * @param subEventId event id
     * @param continueSubscriptionId subscription id
     * @param tag weevent-tag
     * @param ephemeral is ephemeral
     * @return String consumer subscription id, return "" if error
     * @throws BrokerException Exception
     */
    private String handleSubscribe(WebSocketSession session,
                                   String topic,
                                   String groupId,
                                   String headerIdStr,
                                   String subEventId,
                                   String continueSubscriptionId,
                                   String tag,
                                   boolean ephemeral) throws BrokerException {
        log.info("topic: {} group id: {} header subscription id: {}", topic, groupId, headerIdStr);

        String[] curTopicList;
        if (topic.contains(WeEvent.MULTIPLE_TOPIC_SEPARATOR)) {
            log.info("subscribe topic list");
            curTopicList = topic.split(WeEvent.MULTIPLE_TOPIC_SEPARATOR);
        } else {
            curTopicList = new String[]{topic};
        }

        if (!this.iconsumer.isStarted()) {
            log.error("IConsumer not started");
            throw new BrokerException(ErrorCode.UNKNOWN_ERROR);
        }

        // external params
        Map<IConsumer.SubscribeExt, String> ext = new HashMap<>();
        ext.put(IConsumer.SubscribeExt.InterfaceType, WeEventConstants.STOMPTYPE);
        String remoteIp = "";
        if (session.getRemoteAddress() != null) {
            remoteIp = session.getRemoteAddress().getAddress().getHostAddress();
        }
        ext.put(IConsumer.SubscribeExt.RemoteIP, remoteIp);

        if (!StringUtils.isBlank(continueSubscriptionId)) {
            log.info("continueSubscriptionId: {}", continueSubscriptionId);
            ext.put(IConsumer.SubscribeExt.SubscriptionId, continueSubscriptionId);
        }

        if (!StringUtils.isBlank(tag)) {
            ext.put(IConsumer.SubscribeExt.TopicTag, tag);
        }

        if (ephemeral) {
            log.info("ephemeral subscription");
            ext.put(IConsumer.SubscribeExt.Ephemeral, "1");
        }

        IConsumer.ConsumerListener listener;
        listener = new IConsumer.ConsumerListener() {
            @Override
            public void onEvent(String subscriptionId, WeEvent event) {
                log.info("consumer onEvent, subscriptionId: {} event: {}", subscriptionId, event);
                handleOnEvent(headerIdStr, subscriptionId, event, session);
            }

            @Override
            public void onException(Throwable e) {
                log.error("consumer onException", e);
            }
        };

        // support both single/multiple topic
        String subscriptionId = this.iconsumer.subscribe(curTopicList, groupId, subEventId, ext, listener);
        log.info("bind context, session id: {} header subscription id: {} consumer subscription id: {} topic: {}",
                session.getId(), headerIdStr, subscriptionId, Arrays.toString(curTopicList));
        sessionContext.get(session.getId()).put(headerIdStr, Pair.of(subscriptionId, StringUtils.join(curTopicList, WeEvent.MULTIPLE_TOPIC_SEPARATOR)));

        log.info("consumer subscribe success, consumer subscriptionId: {}", subscriptionId);
        return subscriptionId;
    }

    private void handleOnEvent(String headerIdStr,
                               String subscriptionId,
                               WeEvent event,
                               WebSocketSession session) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        // package the return frame
        accessor.setSubscriptionId(headerIdStr);
        accessor.setNativeHeader("subscription-id", subscriptionId);
        accessor.setMessageId(headerIdStr);
        accessor.setDestination(event.getTopic());
        accessor.setContentType(new MimeType("text", "plain", StandardCharsets.UTF_8));

        // set custom properties in header
        for (Map.Entry<String, String> custom : event.getExtensions().entrySet()) {
            accessor.setNativeHeader(custom.getKey(), custom.getValue());
        }

        // set eventId in header
        accessor.setNativeHeader(WeEventConstants.EXTENSIONS_EVENT_ID, event.getEventId());

        // payload == content
        MessageHeaders headers = accessor.getMessageHeaders();
        Message<byte[]> message = MessageBuilder.createMessage(event.getContent(), headers);
        byte[] bytes = new StompEncoder().encode(message);

        // send to remote
        send2Remote(session, new TextMessage(bytes));
    }

    /**
     * @param session stomp session
     * @param headerIdStr subscription id on stomp
     * @return boolean true if ok
     */
    private boolean handleUnSubscribe(WebSocketSession session, String headerIdStr) throws BrokerException {
        if (!sessionContext.get(session.getId()).containsKey(headerIdStr)) {
            log.info("unknown stomp header id, {}", headerIdStr);
            return false;
        }

        String subscriptionId = sessionContext.get(session.getId()).get(headerIdStr).getFirst();
        log.info("session id: {} header id: {} subscriptionId: {}", session.getId(), headerIdStr, subscriptionId);

        // unSubscribe
        boolean result = this.iconsumer.unSubscribe(subscriptionId);
        log.info("consumer unSubscribe, subscriptionId: {} result: {}", subscriptionId, result);
        if (result) {
            // at the same session, remove subscription id in stomp
            sessionContext.get(session.getId()).remove(headerIdStr);
        }

        return result;
    }

    // the following's methods from super class

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("stomp connection in, session id: {} remote: {}", session.getId(), session.getRemoteAddress());

        sessionContext.put(session.getId(), new HashMap<>());
    }

    @Override
    protected void handlePongMessage(WebSocketSession session, PongMessage message) throws Exception {
        log.debug("handle pong message, {}", session.getId());

        super.handlePongMessage(session, message);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.info("exception on transport, {} {}", session.getId(), exception);

        super.handleTransportError(session, exception);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("connection closed, {} CloseStatus: {}", session.getId(), status);

        clearSession(session);
        super.afterConnectionClosed(session, status);
    }

    @Override
    public boolean supportsPartialMessages() {
        return super.supportsPartialMessages();
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!sessionContext.containsKey(session.getId())) {
            log.error("unknown session id, skip it");
            return;
        }

        List<Message<byte[]>> stompMsg = new StompDecoder().decode(ByteBuffer.wrap(message.getPayload().getBytes(StandardCharsets.UTF_8)));
        for (Message<byte[]> msg : stompMsg) {
            this.handleSingleMessage(msg, session);
        }
    }
}
