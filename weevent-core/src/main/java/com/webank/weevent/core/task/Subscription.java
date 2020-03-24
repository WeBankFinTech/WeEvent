package com.webank.weevent.core.task;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.fisco.constant.WeEventConstants;
import com.webank.weevent.core.fisco.util.DataTypeUtils;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.WeEvent;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/*
 * One topic subscription.
 * ##### more details #####
 * Normally, the events published in the future will be notified by MainEventLoop.
 * If the offset is not WeEvent.OFFSET_LAST, then it need a helper task HistoryEventLoop to fetch history events.
 * It works like as followings:
 * HistoryEventLoop(deal with events in caller thread if has in the same block of offset -> target history events -> current block) ===> MainEventLoop
 * HistoryEventLoop aim to fetch target event between offset and current block(current block will float forward while fetching).
 * It's notify task will switch into MainEventLoop while arriving at highest block height(=current block height in MainEventLoop).
 * But the switch action is done by MainEventLoop in another thread, HistoryEventLoop may float forward a few block at the same time.
 * Because once loop (both in HistoryEventLoop and MainEventLoop) is a long blocking task,
 * so we use a merge cache to avoid repeat notify, not a strict consistency. Like ideas showed in optimistic lock.
 *
 * @author matthewliu
 * @since 2019/08/30
 */
@Slf4j
@Getter
@Setter
public class Subscription {
    /**
     * Subscription ID
     */
    private String uuid;

    /**
     * Binding topic.
     */
    private String[] topics;

    /**
     * Binding groupId.
     */
    private String groupId;

    /**
     * event offset.
     */
    private String offset;

    /**
     * optional tag.
     */
    private String tag;

    /**
     * subscribe from which protocol, restful or json rpc, etc.
     */
    private String interfaceType;

    /**
     * subscribe from which ip
     */
    private String remoteIp;

    /**
     * subscribe topic TimeStamp.
     */
    private Date createTimeStamp = new Date();

    /**
     * idle time
     */
    private int idleTime;

    /**
     * Event notify task.
     */
    private NotifyTask notifyTask;

    /**
     * optional, if offset != WeEvent.OFFSET_LAST then need a event loop to fetch history event
     */
    private HistoryEventLoop historyEventLoop;

    /**
     * helper to avoid repeat notify if exist HistoryEventLoop
     * (eventId <-> timestamp), value is not used yet
     */
    private Map<String, Long> mergeCache;

    /**
     * merge cache block area
     */
    private int mergeBlock = 8;

    /**
     * first block in HistoryEventLoop dispatch
     */
    private Long historyBlock = 0L;

    @Override
    public String toString() {
        return "Subscription{" +
                "uuid='" + this.uuid + '\'' +
                ", topic='" + Arrays.toString(this.topics) + '\'' +
                ", groupId='" + this.groupId + '\'' +
                ", offset='" + this.offset + '\'' +
                ", tag='" + this.tag + '\'' +
                '}';
    }

    public Subscription(IBlockChain blockChain, String uuid, String[] topics, String groupId, String offset, String tag, IConsumer.ConsumerListener listener) throws BrokerException {
        if (StringUtils.isBlank(uuid)) {
            this.uuid = UUID.randomUUID().toString();
        } else {
            this.uuid = uuid;
        }

        this.topics = topics;
        this.groupId = groupId;
        this.offset = offset;
        this.tag = tag;
        this.idleTime = blockChain.getIdleTime();

        this.notifyTask = new NotifyTask(this.uuid, this.idleTime, listener);

        // not OFFSET_LAST, need history help task
        if (!WeEvent.OFFSET_LAST.equals(this.offset)) {
            log.info("need history event loop, {}", this);

            Long lastBlock;
            if (WeEvent.OFFSET_FIRST.equals(this.offset)) {
                lastBlock = 0L;
            } else {
                lastBlock = DataTypeUtils.decodeBlockNumber(offset);
            }
            this.historyEventLoop = new HistoryEventLoop(blockChain, this, lastBlock);

            this.historyBlock = this.historyEventLoop.getLastBlock();
            this.mergeCache = new HashMap<>();
        }
    }

    public Long getNotifyingEventCount() {
        return (long) this.notifyTask.getEventQueue().size();
    }

    public Long getNotifiedEventCount() {
        return this.notifyTask.getNotifiedCount();
    }

    public Date getNotifyTimeStamp() {
        return this.notifyTask.getLastTimeStamp();
    }

    // mainLoop = true meanings dispatch from MainEventLoop, always history first then main loop
    public void dispatch(List<WeEvent> events, boolean mainLoop, Long blockHeight) {
        // skip block before notified history block
        if (blockHeight <= this.historyBlock) {
            return;
        }

        List<WeEvent> topicEvents;
        if (mainLoop) {
            // filter the events if from main loop
            topicEvents = filter(events, this.topics, this.tag);
            if (topicEvents.isEmpty()) {
                return;
            }
        } else {
            log.info("dispatch from HistoryEventLoop");

            // record last history block
            if (blockHeight > this.historyBlock) {
                this.historyBlock = blockHeight;
            }

            // already filter if from history
            topicEvents = events;
        }

        // need merge in cache
        if (this.mergeCache != null) {
            topicEvents.removeIf((event) -> this.mergeCache.containsKey(event.getEventId()));
            Long now = System.currentTimeMillis();
            for (WeEvent event : topicEvents) {
                this.mergeCache.put(event.getEventId(), now);
            }

            // cleanup merge cache if needed
            if (mainLoop && blockHeight > this.historyBlock + this.mergeBlock) {
                log.info("HistoryEventLoop finalize merge cache at block: {}", this.mergeBlock);

                this.mergeCache.clear();
                this.mergeCache = null;
            }
        }

        if (topicEvents.isEmpty()) {
            log.info("event list is empty after filter by merge cache");
            return;
        }

        // notify to remote really
        this.notifyTask.push(topicEvents);
    }

    public synchronized void doStart(Executor executor) {
        executor.execute(this.notifyTask);
        if (this.historyEventLoop != null) {
            executor.execute(this.historyEventLoop);
        }
    }

    // can not doStart again after doStop
    public synchronized void doStop() {
        this.notifyTask.doExit();

        // wait task exit really
        StoppableTask.idle(this.idleTime);

        stopHistory();

        // call onClose
        this.notifyTask.getConsumerListener().onClose(this.uuid);
    }

    public synchronized void stopHistory() {
        if (this.historyEventLoop != null) {
            this.historyEventLoop.doExit();
            this.historyEventLoop = null;
        }
    }

    public synchronized boolean tryStopHistory(Long mainLastBlock) {
        if (mainLastBlock > 0 && mainLastBlock <= this.historyEventLoop.getLastBlock()) {
            log.info("switch history to main event loop, {} ---> {}, {}",
                    this.historyEventLoop.getLastBlock(), mainLastBlock, this);

            stopHistory();
            return true;
        }

        return false;
    }

    /*
     * filter event with topic name or pattern
     *
     * @param from original event list
     * @param topics topic name or Pattern list
     * @param tag tag value
     * @return target event list
     */
    public static List<WeEvent> filter(List<WeEvent> from, String[] topics, String tag) {
        List<WeEvent> to = new ArrayList<>();
        for (WeEvent event : from) {
            for (String topic : topics) {
                boolean topic_target = false;
                // subscription in pattern
                if (isTopicPattern(topic)) {
                    if (patternMatch(event.getTopic(), topic)) {
                        topic_target = true;
                    }
                } else { // subscription in normal topic
                    if (topic.equals(event.getTopic())) {
                        topic_target = true;
                    }
                }

                if (topic_target) {
                    // subscription without tag
                    if (StringUtils.isBlank(tag)) {
                        to.add(event);
                    } else {    // subscription in tag plus
                        if (tag.equals(event.getExtensions().get(WeEvent.WeEvent_TAG))) {
                            to.add(event);
                        }
                    }
                }
            }
        }

        return to;
    }

    /**
     * see match
     *
     * @param pattern topic pattern
     * @return true if yes
     */
    public static boolean isTopicPattern(String pattern) {
        if (StringUtils.isBlank(pattern)) {
            return false;
        }
        return pattern.contains("" + WeEvent.WILD_CARD_ALL_LAYER) || pattern.contains("" + WeEvent.WILD_CARD_ONE_LAYER);
    }

    /*
     * see match
     *
     * @param pattern topic pattern
     */
    public static void validateTopicPattern(String pattern) throws BrokerException {
        if (StringUtils.isBlank(pattern)) {
            throw new BrokerException(ErrorCode.PATTERN_INVALID);
        }

        if (pattern.length() > WeEventConstants.TOPIC_NAME_MAX_LENGTH) {
            throw new BrokerException(ErrorCode.TOPIC_EXCEED_MAX_LENGTH);
        }

        for (char x : pattern.toCharArray()) {
            if (x < 32 || x > 128) {
                throw new BrokerException(ErrorCode.TOPIC_CONTAIN_INVALID_CHAR);
            }
        }

        String[] layer = pattern.split(WeEvent.LAYER_SEPARATE);
        if (pattern.contains(WeEvent.WILD_CARD_ONE_LAYER)) {
            for (String x : layer) {
                if (x.contains(WeEvent.WILD_CARD_ONE_LAYER) && !x.equals(WeEvent.WILD_CARD_ONE_LAYER)) {
                    throw new BrokerException(ErrorCode.PATTERN_INVALID);
                }
            }
        } else if (pattern.contains(WeEvent.WILD_CARD_ALL_LAYER)) {
            // only one '#'
            if (StringUtils.countMatches(pattern, WeEvent.WILD_CARD_ALL_LAYER) != 1) {
                throw new BrokerException(ErrorCode.PATTERN_INVALID);
            }

            // '#' must be at last position
            if (!layer[layer.length - 1].equals(WeEvent.WILD_CARD_ALL_LAYER)) {
                throw new BrokerException(ErrorCode.PATTERN_INVALID);
            }
        } else {
            throw new BrokerException(ErrorCode.PATTERN_INVALID);
        }
    }

    /**
     * check is topic name matched the input pattern.
     * see MQTT specification http://public.dhe.ibm.com/software/dw/webservices/ws-mqtt/mqtt-v3r1.html.
     * notice:
     * "com/weevent/test" is invalid
     * "com/weevent/test" is different from "/com/weevent/test"
     *
     * @param topic topic name
     * @param pattern mqtt pattern with wildcard
     * @return true if match
     */
    private static boolean patternMatch(String topic, String pattern) {
        String[] topicLayer = topic.split(WeEvent.LAYER_SEPARATE);
        String[] patternLayer = pattern.split(WeEvent.LAYER_SEPARATE);

        // '+' means 1 layer
        if (pattern.contains(WeEvent.WILD_CARD_ONE_LAYER)) {
            // layer must be same
            if (topicLayer.length != patternLayer.length) {
                return false;
            }

            for (int idx = 0; idx < patternLayer.length; idx++) {
                // the layer except '+' must be match
                if (!patternLayer[idx].equals(WeEvent.WILD_CARD_ONE_LAYER)
                        && !patternLayer[idx].equals(topicLayer[idx])) {
                    return false;
                }
            }
            return true;
        } else if (pattern.contains(WeEvent.WILD_CARD_ALL_LAYER)) {    // '#' means 0 or n layer
            if (!patternLayer[patternLayer.length - 1].equals(WeEvent.WILD_CARD_ALL_LAYER)) {
                log.error("'#' must be in last layer");
                return false;
            }

            // pattern layer must be less then topic
            if (patternLayer.length > topicLayer.length) {
                return false;
            }

            // skip last layer '#'
            for (int idx = 0; idx < patternLayer.length - 1; idx++) {
                // the layer before '#' must be match
                if (!patternLayer[idx].equals(topicLayer[idx])) {
                    return false;
                }
            }
            return true;
        } else {
            log.error("no wildcard character in pattern");
            return false;
        }
    }
}
