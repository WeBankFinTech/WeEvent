package com.webank.weevent.broker.fisco;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.webank.weevent.BrokerApplication;
import com.webank.weevent.JUnitTestBase;
import com.webank.weevent.broker.config.FiscoConfig;
import com.webank.weevent.broker.fisco.util.DataTypeUtils;
import com.webank.weevent.broker.fisco.web3sdk.FiscoBcosDelegate;
import com.webank.weevent.broker.fisco.web3sdk.v2.Web3SDK2Wrapper;
import com.webank.weevent.broker.plugin.IProducer;
import com.webank.weevent.sdk.BrokerException;
import com.webank.weevent.sdk.ErrorCode;
import com.webank.weevent.sdk.SendResult;
import com.webank.weevent.sdk.WeEvent;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.web3j.abi.FunctionEncoder;
import org.fisco.bcos.web3j.abi.TypeReference;
import org.fisco.bcos.web3j.abi.datatypes.Function;
import org.fisco.bcos.web3j.abi.datatypes.Type;
import org.fisco.bcos.web3j.crypto.ExtendedRawTransaction;
import org.fisco.bcos.web3j.crypto.ExtendedTransactionEncoder;
import org.fisco.bcos.web3j.utils.BlockLimit;
import org.fisco.bcos.web3j.utils.Numeric;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * FiscoBcosBroker4Producer Tester.
 *
 * @author websterchen
 * @author matthewliu
 * @version 1.0
 * @since 11/08/2018
 */
@Slf4j
public class FiscoBcosBroker4ProducerTest extends JUnitTestBase {
    private IProducer iProducer;
    private FiscoBcosDelegate fiscoBcosDelegate;

    @Before
    public void before() throws Exception {
        log.info("=============================={}.{}==============================",
                this.getClass().getSimpleName(),
                this.testName.getMethodName());

        this.iProducer = BrokerApplication.applicationContext.getBean("iProducer", IProducer.class);
        this.fiscoBcosDelegate = BrokerApplication.applicationContext.getBean("fiscoBcosDelegate", FiscoBcosDelegate.class);
        Assert.assertNotNull(this.iProducer);
        this.iProducer.startProducer();
        Assert.assertTrue(this.iProducer.open(this.topicName, this.groupId));
    }

    /**
     * Method: startProducer(String topic)
     */
    @Test
    public void testStartProducer() throws Exception {
        Assert.assertTrue(this.iProducer.startProducer());
    }

    /**
     * Method: shutdownProducer()
     */
    @Test
    public void testShutdownProducer() {
        Assert.assertTrue(this.iProducer.shutdownProducer());
    }

    /**
     * Method: publish(WeEvent event)
     */
    @Test
    public void testPublishEvent() throws Exception {
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, "hello world.".getBytes()), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SendResult.SendResultStatus.SUCCESS, sendResult.getStatus());
    }

    /**
     * topic is exist and content is Chinese
     */
    @Test
    public void testPublishTopicExists() throws Exception {
        SendResult dto = this.iProducer.publish(new WeEvent(this.topicName, "中文消息.".getBytes()), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);
        Assert.assertEquals(SendResult.SendResultStatus.SUCCESS, dto.getStatus());
    }

    /**
     * test extensions is null
     */
    @Test
    public void testPublishExtIsNull() throws Exception {
        try {
            SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes(), null), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);
            Assert.assertEquals(SendResult.SendResultStatus.SUCCESS, sendResult.getStatus());
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.EVENT_EXTENSIONS_IS_NUll.getCode(), e.getCode());
        }
    }

    /**
     * extensions contain multiple key and value ,key start with 'weevent-'
     */
    @Test
    public void testPublishExtContainMulKey() throws Exception {
        Map<String, String> ext = new HashMap<>();
        ext.put("weevent-test", "test value");
        ext.put("weevent-test2", "test value2");
        ext.put("weevent-test3", "test value3");
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);

        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    /**
     * extensions contain multiple key and value ,one key not start with 'weevent-'
     */
    @Test
    public void testPublishExtContainMulKeyContainNotInvalidKey() {
        try {
            Map<String, String> ext = new HashMap<>();
            ext.put("weevent-test", "test value");
            ext.put("weevent-test2", "test value2");
            ext.put("test3", "test value3");
            this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes(), ext), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.EVENT_EXTENSIONS_KEY_INVALID.getCode(), e.getCode());
        }
    }

    /**
     * extensions contain one key and value, key not start with 'weevent-'
     */
    @Test
    public void testPublishExtContainNotInvalidKey() {
        try {
            Map<String, String> ext = new HashMap<>();
            ext.put("test1", "test value");
            this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes(), ext), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.EVENT_EXTENSIONS_KEY_INVALID.getCode(), e.getCode());
        }
    }

    /**
     * topic is not exists
     */
    @Test
    public void testPublishTopicNotExist() {
        try {
            String topicNotExists = "fsgdsggdgerer";
            this.iProducer.publish(new WeEvent(topicNotExists, "中文消息.".getBytes()), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TOPIC_NOT_EXIST.getCode(), e.getCode());
        }
    }

    /**
     * topic is blank
     */
    @Test
    public void testPublishTopicIsBlank() {
        try {
            this.iProducer.publish(new WeEvent(" ", "中文消息.".getBytes()), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TOPIC_IS_BLANK.getCode(), e.getCode());
        }
    }

    /**
     * topic is null
     */
    @Test
    public void testPublishTopicIsNull() {
        try {
            this.iProducer.publish(new WeEvent(null, "中文消息.".getBytes()), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TOPIC_IS_BLANK.getCode(), e.getCode());
        }
    }

    /**
     * topic length > 64
     */
    @Test
    public void testPublishTopicOverMaxLen() {
        try {
            String topicNotExists = "fsgdsggdgererqwertyuioplkjhgfdsazxqazwsxedcrfvtgbyhnujmikolppoiuyt";
            this.iProducer.publish(new WeEvent(topicNotExists, "中文消息.".getBytes()), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TOPIC_EXCEED_MAX_LENGTH.getCode(), e.getCode());
        }
    }

    /**
     * topic is exits and content is null
     */
    @Test
    public void testPublishContentIsNull() {
        try {
            this.iProducer.publish(new WeEvent(this.topicName, null), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.EVENT_CONTENT_IS_BLANK.getCode(), e.getCode());
        }
    }

    /**
     * topic is exits and content is blank
     */
    @Test
    public void testPublishContentIsBlank() {
        try {
            byte[] bytes = "".getBytes();
            this.iProducer.publish(new WeEvent(this.topicName, bytes), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.EVENT_CONTENT_IS_BLANK.getCode(), e.getCode());
        }
    }

    /**
     * groupId is null
     */
    @Test
    public void testPublishGroupIdIsNull() throws Exception {
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes()), null).get(transactionTimeout, TimeUnit.MILLISECONDS);
        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    /**
     * groupId is not exist
     */
    @Test
    public void testPublishGroupIdIsNotExist() {
        try {
            this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes()), "4");
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.WEB3SDK_UNKNOWN_GROUP.getCode(), e.getCode());
        }
    }

    /**
     * groupId is not number
     */
    @Test
    public void testPublishGroupIdIsNotNum() {
        try {
            this.iProducer.publish(new WeEvent(this.topicName, "this is only test message".getBytes()), "sfsdf");
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.WEB3SDK_UNKNOWN_GROUP.getCode(), e.getCode());
        }
    }

    /**
     * topic contain special character without in [32,128]
     */
    @Test
    public void testPublishTopicContainSpecialChar() {
        try {
            char[] charStr = {69, 72, 31};
            String illegalTopic = new String(charStr);
            byte[] bytes = "hello world".getBytes();
            this.iProducer.publish(new WeEvent(illegalTopic, bytes), this.groupId);
            Assert.fail();
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TOPIC_CONTAIN_INVALID_CHAR.getCode(), e.getCode());
        }
    }

    /**
     * topic is Chinese character
     */
    @Test
    public void testPublishTopicContainChineseChar() {
        try {
            byte[] bytes = "".getBytes();
            this.iProducer.publish(new WeEvent("中国", bytes), this.groupId);
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TOPIC_CONTAIN_INVALID_CHAR.getCode(), e.getCode());
        }
    }

    /**
     * publish with custom header
     */
    @Test
    public void testPublishTag() throws Exception {
        Map<String, String> ext = new HashMap<>();
        ext.put(WeEvent.WeEvent_TAG, "create");
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, "hello tag: create".getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);
        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    /**
     * publish an externally signed event
     */
    @Test
    public void testPublishWithSigned() throws Exception {
        Map<String, String> ext = new HashMap<>();
        ext.put(WeEvent.WeEvent_SIGN, "true");
        WeEvent event = new WeEvent(this.topicName, "this is a signed message".getBytes(), ext);
        String topicAddress = this.fiscoBcosDelegate.getContractContext(Long.parseLong(this.groupId)).getTopicAddress();

        String rawData = buildData(event);
        ExtendedRawTransaction rawTransaction = getRawTransaction(rawData, topicAddress);

        String signData = signData(rawTransaction);
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, signData.getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);

        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    private String buildData(WeEvent event) throws BrokerException {
        final Function function = new Function(
                "publishWeEvent",
                Arrays.<Type>asList(new org.fisco.bcos.web3j.abi.datatypes.Utf8String(topicName),
                        new org.fisco.bcos.web3j.abi.datatypes.Utf8String(new String(event.getContent(), StandardCharsets.UTF_8)),
                        new org.fisco.bcos.web3j.abi.datatypes.Utf8String(DataTypeUtils.object2Json(event.getExtensions()))),
                Collections.<TypeReference<?>>emptyList());
        return FunctionEncoder.encode(function);
    }

    private ExtendedRawTransaction getRawTransaction(String data, String topicAddress) throws BrokerException {
        Random r = new SecureRandom();
        BigInteger randomid = new BigInteger(250, r);
        String chainId = this.fiscoBcosDelegate.getVersion(Long.parseLong(this.groupId)).getChainID();
        ExtendedRawTransaction rawTransaction =
                ExtendedRawTransaction.createTransaction(
                        randomid,
                        new BigInteger("999999999"),
                        new BigInteger("99999999999"),
                        BigInteger.valueOf(BlockLimit.blockLimit.intValue()),
                        topicAddress,
                        new BigInteger("0"),
                        data,
                        new BigInteger(chainId),
                        new BigInteger(this.groupId),
                        null);
        return rawTransaction;
    }

    private String signData(ExtendedRawTransaction rawTransaction) {
        FiscoConfig fiscoConfig = new FiscoConfig();
        fiscoConfig.load();
        byte[] signedMessage = ExtendedTransactionEncoder.signMessage(rawTransaction, Web3SDK2Wrapper.getCredentials(fiscoConfig));
        return Numeric.toHexString(signedMessage);
    }

}
