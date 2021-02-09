package com.webank.weevent.core.fisco;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.JsonHelper;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.JUnitTestBase;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.dto.ContractContext;
import com.webank.weevent.core.fisco.web3sdk.FiscoBcosDelegate;
import com.webank.weevent.core.fisco.web3sdk.v2.Web3SDKConnector;
import com.webank.weevent.core.fisco.web3sdk.v2.solc10.Topic;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.abi.FunctionEncoder;
import org.fisco.bcos.sdk.abi.TypeReference;
import org.fisco.bcos.sdk.abi.datatypes.Function;
import org.fisco.bcos.sdk.abi.datatypes.Type;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.crypto.keypair.ECDSAKeyPair;
import org.fisco.bcos.sdk.crypto.keypair.SM2KeyPair;
import org.fisco.bcos.sdk.transaction.codec.encode.TransactionEncoderService;
import org.fisco.bcos.sdk.transaction.model.po.RawTransaction;
import org.junit.After;
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
    private String groupId = WeEvent.DEFAULT_GROUP_ID;
    private String topicName = "com.weevent.test";
    private ContractContext contractContext;
    private IProducer iProducer;
    private FiscoConfig fiscoConfig;
    private FiscoBcosDelegate fiscoBcosDelegate;
    private long transactionTimeout = 30000;

    @Before
    public void before() throws Exception {
        log.info("=============================={}.{}==============================",
                this.getClass().getSimpleName(),
                this.testName.getMethodName());

        this.fiscoConfig = new FiscoConfig();
        Assert.assertTrue(this.fiscoConfig.load(""));
        this.fiscoBcosDelegate = new FiscoBcosDelegate();
        this.fiscoBcosDelegate.initProxy(this.fiscoConfig);
        this.iProducer = new FiscoBcosBroker4Producer(this.fiscoBcosDelegate);

        Assert.assertTrue(this.iProducer.startProducer());
        this.contractContext = this.fiscoBcosDelegate.getContractContext(Integer.parseInt(this.groupId));
        Assert.assertNotNull(this.iProducer);
        Assert.assertTrue(this.iProducer.open(this.topicName, this.groupId));
    }

    @After
    public void after() {
        Assert.assertTrue(this.iProducer.shutdownProducer());
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
     * publish an externally signed event by fixed account.
     */
    @Test
    public void testPublishByFixedAccount() throws Exception {
        BcosSDK sdk = Web3SDKConnector.buidBcosSDK(fiscoConfig);
        Client client = Web3SDKConnector.initClient(sdk, Integer.parseInt(this.groupId), this.fiscoConfig);

        Map<String, String> ext = new HashMap<>();
        ext.put(WeEvent.WeEvent_SIGN, "true");
        WeEvent event = new WeEvent(this.topicName, "this is a signed message".getBytes(), ext);
        ContractContext contractContext = this.fiscoBcosDelegate.getContractContext(Integer.parseInt(this.groupId));

        String rawData = buildWeEvent(new FunctionEncoder(client.getCryptoSuite()), event);
        RawTransaction rawTransaction = getRawTransaction(this.groupId, rawData, contractContext);

        String signData = signData(client, client.getCryptoSuite().getCryptoKeyPair(), rawTransaction);
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, signData.getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);

        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    /**
     * publish an externally signed event by external account.
     */
    @Test
    public void testPublishByExternalAccount() throws Exception {
        BcosSDK sdk = Web3SDKConnector.buidBcosSDK(fiscoConfig);
        Client client = Web3SDKConnector.initClient(sdk, Integer.parseInt(this.groupId), this.fiscoConfig);

        CryptoKeyPair externalAccountCryptoKeyPair = getExternalAccountCryptoKeyPair();
        String operatorAddress = externalAccountCryptoKeyPair.getAddress();

        // add operatorAddress for topic
        boolean result = this.iProducer.addOperator(this.groupId, this.topicName, operatorAddress);
        Assert.assertTrue(result);

        // publish event with the above generated externally account
        Map<String, String> ext = new HashMap<>();
        ext.put(WeEvent.WeEvent_SIGN, "true");
        WeEvent event = new WeEvent(this.topicName, "this is a signed message".getBytes(), ext);

        String rawData = buildWeEvent(new FunctionEncoder(client.getCryptoSuite()), event);
        RawTransaction rawTransaction = getRawTransaction(this.groupId, rawData, this.contractContext);

        String signData = signData(client, externalAccountCryptoKeyPair, rawTransaction);

        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, signData.getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);

        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    /**
     * publish an externally signed event by external account.
     */
    @Test
    public void testPublishByExternalAccountNoPermission() throws Exception {
        BcosSDK sdk = Web3SDKConnector.buidBcosSDK(fiscoConfig);
        Client client = Web3SDKConnector.initClient(sdk, Integer.parseInt(this.groupId), this.fiscoConfig);

        Map<String, String> ext = new HashMap<>();
        ext.put(WeEvent.WeEvent_SIGN, "true");
        WeEvent event = new WeEvent(this.topicName, "this is a signed message".getBytes(), ext);

        String rawData = buildWeEvent(new FunctionEncoder(client.getCryptoSuite()), event);
        RawTransaction rawTransaction = getRawTransaction(this.groupId, rawData, this.contractContext);

        CryptoKeyPair externalAccountCryptoKeyPair = getExternalAccountCryptoKeyPair();

        String signData = signData(client, externalAccountCryptoKeyPair, rawTransaction);
        SendResult sendResult = this.iProducer.publish(new WeEvent(this.topicName, signData.getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);

        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.NO_PERMISSION);
    }

    /**
     * publish event,transactionHex illegal.
     */
    @Test
    public void testPublishTransactionHexIllegal() throws Exception {
        Map<String, String> ext = new HashMap<>();
        ext.put(WeEvent.WeEvent_SIGN, "true");

        String signData = "asd";
        try {
            this.iProducer.publish(new WeEvent(this.topicName, signData.getBytes(), ext), this.groupId).get(transactionTimeout, TimeUnit.MILLISECONDS);
        } catch (BrokerException e) {
            Assert.assertEquals(ErrorCode.TRANSACTIONHEX_ILLEGAL.getCode(), e.getCode());
        }
    }

    private String buildWeEvent(FunctionEncoder functionEncoder, WeEvent event) throws BrokerException {
        final Function function = new Function(
                Topic.FUNC_PUBLISHWEEVENT,
                Arrays.<Type>asList(new org.fisco.bcos.sdk.abi.datatypes.Utf8String(event.getTopic()),
                        new org.fisco.bcos.sdk.abi.datatypes.Utf8String(new String(event.getContent(), StandardCharsets.UTF_8)),
                        new org.fisco.bcos.sdk.abi.datatypes.Utf8String(JsonHelper.object2Json(event.getExtensions()))),
                Collections.<TypeReference<?>>emptyList());
        return functionEncoder.encode(function);
    }

    private RawTransaction getRawTransaction(String groupId, String data, ContractContext contractContext) {
        Random r = new SecureRandom();
        BigInteger randomid = new BigInteger(250, r);
        RawTransaction rawTransaction =
                RawTransaction.createTransaction(
                        randomid,
                        BigInteger.valueOf(contractContext.getGasPrice()),
                        BigInteger.valueOf(contractContext.getGasLimit()),
                        BigInteger.valueOf(contractContext.getBlockLimit()),
                        contractContext.getTopicAddress(),
                        BigInteger.ZERO,
                        data,
                        new BigInteger(contractContext.getChainId()),
                        new BigInteger(groupId),
                        null);

        return rawTransaction;
    }

    private String signData(Client client, CryptoKeyPair cryptoKeyPair, RawTransaction rawTransaction) {
        TransactionEncoderService transactionEncoderService = new TransactionEncoderService(client.getCryptoSuite());
        return transactionEncoderService.encodeAndSign(rawTransaction, cryptoKeyPair);
    }

    private CryptoKeyPair getExternalAccountCryptoKeyPair() {
        if (fiscoConfig.getWeEventCoreConfig().getWeb3sdkEncryptType().equals("ECDSA_TYPE")) {
            return (new ECDSAKeyPair()).generateKeyPair();
        } else {
            return (new SM2KeyPair()).generateKeyPair();
        }
    }

}
