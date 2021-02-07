package com.webank.weevent.core.fisco;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.dto.SubscriptionInfo;
import com.webank.weevent.core.fisco.util.ParamCheckUtils;
import com.webank.weevent.core.fisco.web3sdk.FiscoBcosDelegate;
import com.webank.weevent.core.task.IBlockChain;
import com.webank.weevent.core.task.MainEventLoop;
import com.webank.weevent.core.task.Subscription;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Event broker's consumer implement in FISCO-BCOS.
 * This class is thread safe.
 *
 * @author matthewliu
 * @since 2018/11/02
 */
@Slf4j
public class FiscoBcosBroker4Consumer extends FiscoBcosTopicAdmin implements IConsumer, IBlockChain, FiscoBcosDelegate.IBlockEventListener {
    /**
     * Group ID <-> Subscription over AMOP
     */
    private final Map<Integer, AMOPSubscription> AMOPSubscriptions;

    /**
     * Subscription ID <-> Subscription
     */
    private final Map<String, Subscription> subscriptions = new ConcurrentHashMap<>();

    /**
     * Group ID <-> MainEventLoop
     */
    private final Map<Long, MainEventLoop> mainEventLoops = new ConcurrentHashMap<>();

    /**
     * daemon executor
     */
    private final Executor executor;

    /**
     * Whether the Consumer has started
     */
    private boolean consumerStarted = false;

    /**
     * idle time if no new block
     */
    private final int idleTime;

    public FiscoBcosBroker4Consumer(FiscoBcosDelegate fiscoBcosDelegate) {
        super(fiscoBcosDelegate);

        this.AMOPSubscriptions = fiscoBcosDelegate.initAMOP();
        this.executor = fiscoBcosDelegate.getThreadPool();
        this.idleTime = fiscoBcosDelegate.getFiscoConfig().getWeEventCoreConfig().getConsumerIdleTime();
        fiscoBcosDelegate.setListener(this);
    }

    private static boolean isEventId(String offset) {
        return !WeEvent.OFFSET_FIRST.equals(offset) && !WeEvent.OFFSET_LAST.equals(offset);
    }

    // topic may be a topic pattern
    @Override
    public String subscribe(String topic, String groupIdStr, String offset,
                            @NonNull Map<SubscribeExt, String> ext,
                            @NonNull ConsumerListener listener) throws BrokerException {
        String groupId = selectGroupId(groupIdStr);
        this.validateGroupId(groupId);
        ParamCheckUtils.validateOffset(offset);

        Long currentBlock = fiscoBcosDelegate.getBlockHeight(Integer.parseInt(groupId));
        // topic pattern
        if (Subscription.isTopicPattern(topic)) {
            Subscription.validateTopicPattern(topic);
            if (StringUtils.isNumeric(offset)) {
                ParamCheckUtils.validateBlockHeight(offset, currentBlock);
            } else if (isEventId(offset)) {
                // not a topic name
                ParamCheckUtils.validateEventId("", offset, currentBlock);
            }
        } else {    // topic name
            ParamCheckUtils.validateTopicName(topic);

            // check topic exist
            if (!this.exist(topic, groupId)) {
                throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
            }

            if (StringUtils.isNumeric(offset)) {
                ParamCheckUtils.validateBlockHeight(offset, currentBlock);
            } else if (isEventId(offset)) {
                ParamCheckUtils.validateEventId(topic, offset, currentBlock);
            }
        }

        log.info("subscribe groupId: {} topic: {} offset: {} ext: {}", groupId, topic, offset, ext);
        return subscribeTopic(topic, Long.valueOf(groupId), offset, ext, listener);
    }

    @Override
    public String subscribe(String[] topics, String groupIdStr, String offset,
                            @NonNull Map<SubscribeExt, String> ext,
                            @NonNull ConsumerListener listener) throws BrokerException {
        // check params
        if (topics == null || topics.length == 0) {
            throw new BrokerException(ErrorCode.TOPIC_LIST_IS_NULL);
        }

        String groupId = selectGroupId(groupIdStr);
        this.validateGroupId(groupId);
        ParamCheckUtils.validateOffset(offset);

        Long currentBlock = fiscoBcosDelegate.getBlockHeight(Integer.parseInt(groupId));
        if (StringUtils.isNumeric(offset)) {
            ParamCheckUtils.validateBlockHeight(offset, currentBlock);
        } else if (isEventId(offset)) {
            // do not validate topic name and eventId if more then one topic
            ParamCheckUtils.validateEventId(topics.length > 1 ? "" : topics[0], offset, currentBlock);
        }

        for (String topic : topics) {
            if (Subscription.isTopicPattern(topic)) {
                Subscription.validateTopicPattern(topic);
            } else {
                ParamCheckUtils.validateTopicName(topic);

                // check topic exist
                if (!this.exist(topic, groupId)) {
                    throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
                }
            }
        }

        log.info("subscribe groupId:{} topics: {} offset: {} ext:{}", groupId, Arrays.toString(topics), offset, ext);
        return subscribeTopic(topics, Long.valueOf(groupId), offset, ext, listener);
    }

    private String subscribeTopic(String topic, Long groupId, String offset, Map<SubscribeExt, String> ext, ConsumerListener listener) throws BrokerException {
        String[] topics = {topic};
        return subscribeTopic(topics, groupId, offset, ext, listener);
    }

    private String subscribeTopic(String[] topics, Long groupId, String offset, Map<SubscribeExt, String> ext, ConsumerListener listener) throws BrokerException {
        // support Ephemeral
        if (ext.containsKey(SubscribeExt.Ephemeral)) {
            AMOPSubscription amopSubscription = this.AMOPSubscriptions.get(groupId);
            return amopSubscription.addTopic(topics[0], listener);
        }

        // external params
        String interfaceType = "";
        if (ext.containsKey(SubscribeExt.InterfaceType)) {
            interfaceType = ext.get(SubscribeExt.InterfaceType);
        }
        String remoteIp = "";
        if (ext.containsKey(SubscribeExt.RemoteIP)) {
            remoteIp = ext.get(SubscribeExt.RemoteIP);
        }
        String tag = "";
        if (ext.containsKey(SubscribeExt.TopicTag)) {
            tag = ext.get(SubscribeExt.TopicTag);
            if (StringUtils.isBlank(tag)) {
                throw new BrokerException(ErrorCode.TOPIC_TAG_IS_BLANK);
            }
        }

        // custom input subscriptionId, support in STOMP
        String subscriptionId = "";
        if (ext.containsKey(SubscribeExt.SubscriptionId)) {
            subscriptionId = ext.get(SubscribeExt.SubscriptionId);
            ParamCheckUtils.validateSubscriptionId(subscriptionId);
        }

        if (this.subscriptions.containsKey(subscriptionId)) {
            log.info("already exist subscription: {}", subscriptionId);

            // subscription in MQTT and STOMP is connection orientated, will auto unsubscribe when connection lost.
            // so it's something wrong when already exist
            throw new BrokerException(ErrorCode.SUBSCRIPTIONID_ALREADY_EXIST);
        }

        // new subscribe
        Subscription subscription = new Subscription(this,
                subscriptionId,
                topics,
                String.valueOf(groupId),
                offset,
                tag,
                listener);
        subscription.setMergeBlock(fiscoBcosDelegate.getFiscoConfig().getWeEventCoreConfig().getConsumerHistoryMergeBlock());
        subscription.setInterfaceType(interfaceType);
        subscription.setRemoteIp(remoteIp);

        this.subscriptions.put(subscription.getUuid(), subscription);
        this.mainEventLoops.get(groupId).addSubscription(subscription);

        return subscription.getUuid();
    }

    @Override
    public boolean unSubscribe(String subscriptionId) throws BrokerException {
        if (StringUtils.isBlank(subscriptionId)) {
            throw new BrokerException(ErrorCode.SUBSCRIPTIONID_IS_BLANK);
        }

        // support Ephemeral
        String[] tokens = subscriptionId.split(AMOPSubscription.SEPARATE);
        if (tokens.length == 2) {
            AMOPSubscription amopSubscription = this.AMOPSubscriptions.get(Long.valueOf(tokens[0]));
            amopSubscription.removeTopic(subscriptionId);
            return true;
        }

        if (!this.subscriptions.containsKey(subscriptionId)) {
            log.warn("not exist subscriptionId {}", subscriptionId);
            throw new BrokerException(ErrorCode.SUBSCRIPTIONID_NOT_EXIST);
        }

        Subscription subscription = this.subscriptions.get(subscriptionId);
        this.mainEventLoops.get(Long.valueOf(subscription.getGroupId())).removeSubscription(subscription);
        this.subscriptions.remove(subscriptionId);

        log.info("unSubscribe success, subscriptionId: {}", subscriptionId);
        return true;
    }

    @Override
    public boolean isStarted() {
        return this.consumerStarted;
    }

    @Override
    public synchronized boolean startConsumer() throws BrokerException {
        if (this.consumerStarted) {
            throw new BrokerException(ErrorCode.CONSUMER_ALREADY_STARTED);
        }

        // load MainEventLoop with configuration
        for (String groupId : fiscoBcosDelegate.listGroupId()) {
            MainEventLoop mainEventLoop = new MainEventLoop(this.executor, this, groupId);
            mainEventLoop.doStart();
            Long gid = Long.valueOf(groupId);
            this.mainEventLoops.put(gid, mainEventLoop);
        }

        this.consumerStarted = true;
        log.info("start consumer finish");
        return true;
    }

    @Override
    public synchronized boolean shutdownConsumer() {
        // stop main event loop and referred subscription
        for (Map.Entry<Long, MainEventLoop> mainEventLoop : this.mainEventLoops.entrySet()) {
            mainEventLoop.getValue().doStop();
        }
        this.mainEventLoops.clear();
        this.subscriptions.clear();

        this.consumerStarted = false;

        log.info("shutdown consumer finish");
        return true;
    }

    @Override
    public synchronized Map<String, SubscriptionInfo> listSubscription(String groupIdStr) throws BrokerException {
        String groupId = selectGroupId(groupIdStr);
        this.validateGroupId(groupId);

        Map<String, SubscriptionInfo> subscribeIdList = new HashMap<>();
        for (Map.Entry<String, Subscription> entry : this.subscriptions.entrySet()) {
            Subscription subscription = entry.getValue();
            if (groupId.equals(subscription.getGroupId())) {
                SubscriptionInfo subscriptionInfo = SubscriptionInfo.fromSubscription(subscription);
                subscribeIdList.put(subscription.getUuid(), subscriptionInfo);
            }
        }

        log.debug("subscriptions: {}", this.subscriptions.toString());
        return subscribeIdList;
    }

    // methods from IBlockChain
    @Override
    public int getIdleTime() {
        return this.idleTime;
    }

    @Override
    public boolean hasBlockEventNotify() {
        return fiscoBcosDelegate.supportBlockEventNotify();
    }

    @Override
    public List<WeEvent> loop(Long blockNum, String groupId) throws BrokerException {
        return fiscoBcosDelegate.loop(blockNum, Integer.parseInt(groupId));
    }

    // method from FiscoBcosDelegate.IBlockEventListener
    @Override
    public void onEvent(Long groupId, Long blockHeight) {
        if (this.mainEventLoops.containsKey(groupId)) {
            this.mainEventLoops.get(groupId).onNewBlock(blockHeight);
        }
    }
}
