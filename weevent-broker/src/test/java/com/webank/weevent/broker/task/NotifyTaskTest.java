package com.webank.weevent.broker.task;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.webank.weevent.JUnitTestBase;
import com.webank.weevent.broker.plugin.IConsumer;
import com.webank.weevent.sdk.WeEvent;

import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * NotifyTask Tester.
 *
 * @author matthewliu
 * @version 1.0
 * @since 09/02/2019
 */
@Slf4j
public class NotifyTaskTest extends JUnitTestBase {
    private String subscriptionId = "abc";
    private ThreadPoolTaskExecutor threadPoolTaskExecutor;
    private final long wait3s = 3000;
    private WeEvent event = new WeEvent(topicName, "hello wrold".getBytes(StandardCharsets.UTF_8));

    static class MyListener implements IConsumer.ConsumerListener {
        public long received = 0;
        public String subscriptionId;

        @Override
        public void onEvent(String subscriptionId, WeEvent event) {
            this.subscriptionId = subscriptionId;
            received++;
        }

        @Override
        public void onException(Throwable e) {
            received = -10000;
        }
    }

    @Before
    public void before() {
        this.threadPoolTaskExecutor = new ThreadPoolTaskExecutor();
        this.threadPoolTaskExecutor.initialize();
    }

    @After
    public void after() {
        this.threadPoolTaskExecutor.destroy();
    }

    /**
     * push normal
     */
    @Test
    public void testPush() throws Exception {
        log.info("===================={}", this.testName.getMethodName());

        MyListener listener = new MyListener();
        NotifyTask notifyTask = new NotifyTask(this.subscriptionId, 1000, listener);
        this.threadPoolTaskExecutor.execute(notifyTask);

        List<WeEvent> data = new ArrayList<>();
        this.event.setEventId("a");
        data.add(this.event);

        notifyTask.push(data);
        Thread.sleep(wait3s);

        Assert.assertEquals(listener.received, 1);
        Assert.assertEquals(subscriptionId, listener.subscriptionId);
        Assert.assertEquals(notifyTask.getNotifiedCount(), listener.received);
    }

    /**
     * push 2 event in one list
     */
    @Test
    public void testPush2() throws Exception {
        log.info("===================={}", this.testName.getMethodName());

        MyListener listener = new MyListener();
        NotifyTask notifyTask = new NotifyTask(this.subscriptionId, 1000, listener);
        this.threadPoolTaskExecutor.execute(notifyTask);

        List<WeEvent> data = new ArrayList<>();
        this.event.setEventId("a");
        data.add(this.event);
        this.event.setEventId("b");
        data.add(this.event);

        notifyTask.push(data);
        Thread.sleep(wait3s);

        Assert.assertEquals(listener.received, 2);
        Assert.assertEquals(subscriptionId, listener.subscriptionId);
        Assert.assertEquals(notifyTask.getNotifiedCount(), listener.received);
    }

    /**
     * push again
     */
    @Test
    public void testPush3() throws Exception {
        log.info("===================={}", this.testName.getMethodName());

        MyListener listener = new MyListener();
        NotifyTask notifyTask = new NotifyTask(this.subscriptionId, 1000, listener);
        this.threadPoolTaskExecutor.execute(notifyTask);

        List<WeEvent> data1 = new ArrayList<>();
        this.event.setEventId("a");
        data1.add(this.event);
        this.event.setEventId("b");
        data1.add(this.event);
        notifyTask.push(data1);

        List<WeEvent> data2 = new ArrayList<>();
        this.event.setEventId("c");
        data2.add(this.event);
        notifyTask.push(data2);

        Thread.sleep(wait3s);

        Assert.assertEquals(listener.received, 3);
        Assert.assertEquals(subscriptionId, listener.subscriptionId);
        Assert.assertEquals(notifyTask.getNotifiedCount(), listener.received);
    }

    /**
     * push merged
     */
    public void testPush4() throws Exception {
        log.info("===================={}", this.testName.getMethodName());

        MyListener listener = new MyListener();
        NotifyTask notifyTask = new NotifyTask(this.subscriptionId, 1000, listener);
        this.threadPoolTaskExecutor.execute(notifyTask);

        List<WeEvent> data1 = new ArrayList<>();
        this.event.setEventId("a");
        data1.add(this.event);
        this.event.setEventId("b");
        data1.add(this.event);
        notifyTask.push(data1);

        List<WeEvent> data2 = new ArrayList<>();
        this.event.setEventId("a");
        data2.add(this.event);
        this.event.setEventId("b");
        data2.add(this.event);
        notifyTask.push(data2);

        Thread.sleep(wait3s);

        Assert.assertEquals(listener.received, 2);
        Assert.assertEquals(subscriptionId, listener.subscriptionId);
        Assert.assertEquals(notifyTask.getNotifiedCount(), listener.received);
    }
} 
