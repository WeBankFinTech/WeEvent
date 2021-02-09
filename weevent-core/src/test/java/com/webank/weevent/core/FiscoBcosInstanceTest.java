package com.webank.weevent.core;

import java.util.HashMap;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.WeEvent;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * FiscoBcosInstance Tester.
 *
 * @author <matthewliu>
 * @version 1.0
 * @since <pre>03/01/2020</pre>
 */
@Slf4j
public class FiscoBcosInstanceTest extends JUnitTestBase {
    private String groupId = WeEvent.DEFAULT_GROUP_ID;
    private String topicName = "com.weevent.test";

    private FiscoConfig fiscoConfig;

    private FiscoBcosInstance fiscoBcosInstance;

    private IProducer iProducer;

    @Before
    public void before() throws BrokerException {
        log.info("=============================={}.{}==============================",
                this.getClass().getSimpleName(),
                this.testName.getMethodName());

        // Another choice to initialize FiscoConfig:
        // weevent-core.jar contains the FiscoConfig bean, you can scan it in spring context.
        // like this:
        // @SpringBootApplication(scanBasePackages = {"com.webank.weevent.broker", "com.webank.weevent.core.config"})
        // "com.webank.weevent.broker" is package name of a spring boot server
        this.fiscoConfig = new FiscoConfig();
        Assert.assertTrue(this.fiscoConfig.load(""));
        this.fiscoBcosInstance = new FiscoBcosInstance(this.fiscoConfig);
        this.iProducer = this.fiscoBcosInstance.buildProducer();
        Assert.assertTrue(this.iProducer.startProducer());
        Assert.assertTrue(this.iProducer.open(this.topicName, this.groupId));
    }

    /**
     * Method: buildProducer()
     */
    @Test
    public void testBuildProducer() throws Exception {
        WeEvent weEvent = new WeEvent(this.topicName, "hello weevent".getBytes());
        SendResult sendResult = this.iProducer.publish(weEvent, this.groupId, this.fiscoConfig.getWeEventCoreConfig().getTimeout());
        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    /**
     * Method: buildConsumer()
     */
    @Test
    public void testBuildConsumer() throws Exception {
        IConsumer iConsumer = this.fiscoBcosInstance.buildConsumer();
        iConsumer.startConsumer();
        Assert.assertTrue(iConsumer.isStarted());

        String subscriptionId = iConsumer.subscribe(this.topicName, this.groupId, WeEvent.OFFSET_LAST, new HashMap<>(), new IConsumer.ConsumerListener() {
            /**
             * @param subscriptionId binding which subscription
             * @param event the event
             */
            @Override
            public void onEvent(String subscriptionId, WeEvent event) {
                // Called while new event arrived.

            }

            @Override
            public void onException(Throwable e) {
                // Called while raise exception.

            }
        });

        Thread.sleep(3000);
        iConsumer.unSubscribe(subscriptionId);
    }
} 
