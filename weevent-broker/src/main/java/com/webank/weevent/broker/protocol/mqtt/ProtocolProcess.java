package com.webank.weevent.broker.protocol.mqtt;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.webank.weevent.broker.config.WeEventConfig;
import com.webank.weevent.broker.entiry.AccountEntity;
import com.webank.weevent.broker.entiry.AuthorSessionsParam;
import com.webank.weevent.broker.enums.IsDeleteEnum;
import com.webank.weevent.broker.enums.PermissionEnum;
import com.webank.weevent.broker.protocol.mqtt.command.Connect;
import com.webank.weevent.broker.protocol.mqtt.command.DisConnect;
import com.webank.weevent.broker.protocol.mqtt.command.PingReq;
import com.webank.weevent.broker.protocol.mqtt.command.PubAck;
import com.webank.weevent.broker.protocol.mqtt.command.Publish;
import com.webank.weevent.broker.protocol.mqtt.command.Subscribe;
import com.webank.weevent.broker.protocol.mqtt.command.UnSubscribe;
import com.webank.weevent.broker.protocol.mqtt.store.AuthService;
import com.webank.weevent.broker.protocol.mqtt.store.MessageIdStore;
import com.webank.weevent.broker.protocol.mqtt.store.PersistSession;
import com.webank.weevent.broker.protocol.mqtt.store.SessionContext;
import com.webank.weevent.broker.protocol.mqtt.store.SessionStore;
import com.webank.weevent.broker.repository.AccountRepository;
import com.webank.weevent.broker.utils.ZKStore;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttConnAckMessage;
import io.netty.handler.codec.mqtt.MqttConnAckVariableHeader;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttConnectReturnCode;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttIdentifierRejectedException;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttUnacceptableProtocolVersionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * @author websterchen
 * @author matthewliu
 * @version v1.0
 * @since 2019/6/2
 */
@Slf4j
@Component
public class ProtocolProcess {
    // fix length of message id in variableHeader
    public static int fixLengthOfMessageId = 2;

    private final int heartBeat;

    private final SessionStore sessionStore;
    // session id(channel id if from tcp) <-> clientId
    private final Map<String, AuthorSessionsParam> authorSessions = new ConcurrentHashMap<>();
    private final MessageIdStore messageIdStore = new MessageIdStore();

    // MQTT commands
    private final Connect connect;
    private final PingReq pingReq;
    private final Publish publish;
    private final PubAck pubAck;
    private final Subscribe subscribe;
    private final UnSubscribe unSubscribe;
    private final DisConnect disConnect;

    private final Environment environment;
    private final AccountRepository accountRepository;

    @Autowired
    public ProtocolProcess(Environment environment,
                           WeEventConfig weEventConfig,
                           FiscoConfig fiscoConfig,
                           IProducer producer,
                           IConsumer consumer,
                           AccountRepository accountRepository) throws BrokerException {
        boolean auth = environment.getProperty("spring.security.user.auth", Boolean.class, false);
        AuthService authService = new AuthService(auth, accountRepository);

        // try to initialize ZKStore
        boolean zookeeper = environment.getProperty("spring.cloud.zookeeper.enabled", Boolean.class, true);
        String connectString = environment.getProperty("spring.cloud.zookeeper.connect-string", String.class, "");
        ZKStore<PersistSession> zkStore = null;
        if (zookeeper && !StringUtils.isEmpty(connectString)) {
            log.info("try to initialize ZKStore to persist MQTT session");
            zkStore = new ZKStore<>(PersistSession.class, "/WeEvent/mqtt", connectString);
        }
        this.sessionStore = new SessionStore(producer, consumer, fiscoConfig.getWeEventCoreConfig().getTimeout(), this.messageIdStore, zkStore);
        this.heartBeat = weEventConfig.getKeepAlive();

        this.connect = new Connect(authService, this.sessionStore);
        this.pingReq = new PingReq();
        this.publish = new Publish(this.sessionStore);
        this.pubAck = new PubAck(this.messageIdStore);
        this.subscribe = new Subscribe(this.sessionStore);
        this.unSubscribe = new UnSubscribe(this.sessionStore);
        this.disConnect = new DisConnect(this.sessionStore);
        this.environment = environment;
        this.accountRepository = accountRepository;
    }

    public int getHeartBeat() {
        return heartBeat;
    }

    public void cleanSession(String sessionId) {
        if (this.authorSessions.containsKey(sessionId)) {
            log.info("clean session: {}", sessionId);

            String clientId = this.authorSessions.get(sessionId).getClientId();
            this.sessionStore.removeSession(clientId);
            this.authorSessions.remove(sessionId);
        }
    }

    // get response message if needed in decode failure
    public Optional<MqttMessage> getDecodeFailureRsp(MqttMessage msg) {
        if (msg.decoderResult().isFailure()) {
            log.error("decode message failed, {}", msg.decoderResult());

            if (msg.fixedHeader().messageType() == MqttMessageType.CONNECT) {
                MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_LEAST_ONCE, false, 0);

                Throwable cause = msg.decoderResult().cause();
                if (cause instanceof MqttUnacceptableProtocolVersionException) {
                    // Unsupported protocol
                    MqttMessage rsp = MqttMessageFactory.newMessage(fixedHeader,
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false), null);
                    return Optional.of(rsp);
                } else if (cause instanceof MqttIdentifierRejectedException) {
                    // clientId illegal
                    MqttMessage rsp = MqttMessageFactory.newMessage(fixedHeader,
                            new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_IDENTIFIER_REJECTED, false), null);
                    return Optional.of(rsp);
                }
            }
        }

        return Optional.empty();
    }

    // CONNECT is different from the other command
    public MqttConnAckMessage processConnect(MqttConnectMessage msg, SessionContext sessionData) {
        log.info("CONNECT, client id: {}", sessionData.getClientId());

        MqttFixedHeader fixedHeader = new MqttFixedHeader(MqttMessageType.CONNACK, false, MqttQoS.AT_LEAST_ONCE, false, 0);

        if (this.authorSessions.containsKey(sessionData.getSessionId())) {
            log.error("MUST CONNECT only once in a connection");
            MqttMessage rsp = MqttMessageFactory.newMessage(fixedHeader,
                    new MqttConnAckVariableHeader(MqttConnectReturnCode.CONNECTION_REFUSED_UNACCEPTABLE_PROTOCOL_VERSION, false), null);
            return (MqttConnAckMessage) rsp;
        }

        MqttConnAckMessage rsp = (MqttConnAckMessage) this.connect.processConnect(msg, sessionData);
        // if accept
        if (rsp.variableHeader().connectReturnCode() == MqttConnectReturnCode.CONNECTION_ACCEPTED) {
            AuthorSessionsParam sessionsParam = AuthorSessionsParam.builder().clientId(sessionData.getClientId())
                    .userName("user").build();
            this.authorSessions.put(sessionData.getSessionId(), sessionsParam);
        }
        return rsp;
    }

    public MqttPublishMessage genWillMessage(MqttConnectMessage connectMessage) {
        if (connectMessage.variableHeader().isWillFlag()) {
            log.info("get will message from client");

            MqttMessage msg = MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.valueOf(connectMessage.variableHeader().willQos()), connectMessage.variableHeader().isWillRetain(), 0),
                    new MqttPublishVariableHeader(connectMessage.payload().willTopic(), 0), Unpooled.buffer().writeBytes(connectMessage.payload().willMessageInBytes()));

            return (MqttPublishMessage) msg;
        }

        return null;
    }

    public Optional<MqttMessage> process(MqttMessage req, String sessionId, String remoteIp) throws BrokerException {
        if (!this.authorSessions.containsKey(sessionId)) {
            log.error("MUST CONNECT first, skip it");
            throw new BrokerException(ErrorCode.MQTT_CONNECT_CONFLICT);
        }

        String clientId = this.authorSessions.get(sessionId).getClientId();
        if (!this.sessionStore.existSession(clientId)) {
            log.error("unknown clientId, skip it");
            throw new BrokerException(ErrorCode.MQTT_UNKNOWN_CLIENT_ID);
        }

        boolean auth = environment.getProperty("spring.security.user.topic.auth", Boolean.class, false);
        String permission = PermissionEnum.ALL.getCode();
        String topicName = "";
        String userTopicName = "";
        if(auth) {
        	String userName = this.authorSessions.get(sessionId).getUserName();
            AccountEntity accountEntity = accountRepository.findAllByUserNameAndDeleteAt(userName, IsDeleteEnum.NOT_DELETED.getCode());
            if (null != accountEntity) {
                permission = accountEntity.getPermission();
                userTopicName = accountEntity.getTopicName();
            }
            topicName = ((MqttPublishVariableHeader) req.variableHeader()).topicName();
        }

        switch (req.fixedHeader().messageType()) {
            case PINGREQ:
                return this.pingReq.process(req, clientId, remoteIp);

            case PUBLISH:
                if (auth || userTopicName.contains(topicName) || PermissionEnum.SUBSCRIBE.getCode().equals(permission)) {
                    log.error("not publish permission");
                    throw new BrokerException(ErrorCode.MQTT_NOT_PERMISSION);
                }
                return this.publish.process(req, clientId, remoteIp);

            case PUBACK:
                return this.pubAck.process(req, clientId, remoteIp);

            case SUBSCRIBE:
                if (auth || userTopicName.contains(topicName) || PermissionEnum.PUBLISH.getCode().equals(permission)) {
                    log.error("not subscribe permission");
                    throw new BrokerException(ErrorCode.MQTT_NOT_PERMISSION);
                }
                return this.subscribe.process(req, clientId, remoteIp);

            case UNSUBSCRIBE:
                return this.unSubscribe.process(req, clientId, remoteIp);

            case DISCONNECT:
                return this.disConnect.process(req, clientId, remoteIp);

            default:
                log.error("DO NOT support MQTT command, {}", req.fixedHeader().messageType());
                throw new BrokerException(ErrorCode.MQTT_UNKNOWN_COMMAND);
        }
    }
}
