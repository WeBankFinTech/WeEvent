package com.webank.weevent.core.fisco.web3sdk;


import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.TopicInfo;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.dto.ContractContext;
import com.webank.weevent.core.dto.GroupGeneral;
import com.webank.weevent.core.dto.ListPage;
import com.webank.weevent.core.dto.TbBlock;
import com.webank.weevent.core.dto.TbNode;
import com.webank.weevent.core.dto.TbTransHash;
import com.webank.weevent.core.fisco.constant.WeEventConstants;
import com.webank.weevent.core.fisco.util.DataTypeUtils;
import com.webank.weevent.core.fisco.util.ParamCheckUtils;
import com.webank.weevent.core.fisco.web3sdk.v2.CRUDAddress;
import com.webank.weevent.core.fisco.web3sdk.v2.SupportedVersion;
import com.webank.weevent.core.fisco.web3sdk.v2.Web3SDK2Wrapper;
import com.webank.weevent.core.fisco.web3sdk.v2.Web3SDKConnector;
import com.webank.weevent.core.fisco.web3sdk.v2.solc10.Topic;
import com.webank.weevent.core.fisco.web3sdk.v2.solc10.TopicController;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.abi.FunctionReturnDecoder;
import org.fisco.bcos.sdk.abi.TypeReference;
import org.fisco.bcos.sdk.abi.Utils;
import org.fisco.bcos.sdk.abi.datatypes.Type;
import org.fisco.bcos.sdk.abi.datatypes.generated.Uint256;
import org.fisco.bcos.sdk.abi.datatypes.generated.Uint32;
import org.fisco.bcos.sdk.abi.datatypes.generated.tuples.generated.Tuple1;
import org.fisco.bcos.sdk.abi.datatypes.generated.tuples.generated.Tuple2;
import org.fisco.bcos.sdk.abi.datatypes.generated.tuples.generated.Tuple3;
import org.fisco.bcos.sdk.abi.datatypes.generated.tuples.generated.Tuple8;
import org.fisco.bcos.sdk.amop.Amop;
import org.fisco.bcos.sdk.amop.AmopMsgOut;
import org.fisco.bcos.sdk.amop.AmopResponse;
import org.fisco.bcos.sdk.amop.AmopResponseCallback;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.client.protocol.response.SendTransaction;
import org.fisco.bcos.sdk.contract.Contract;
import org.fisco.bcos.sdk.model.RetCode;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.model.TransactionReceiptStatus;
import org.fisco.bcos.sdk.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.service.GroupManagerService;
import org.fisco.bcos.sdk.transaction.codec.decode.ReceiptParser;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;
import org.fisco.bcos.sdk.utils.Numeric;


/**
 * Access to FISCO-BCOS 2.x.
 *
 * @author matthewliu
 * @since 2019/04/28
 */
@Slf4j
public class FiscoBcos2 {
    // config
    private final FiscoConfig fiscoConfig;

    private BcosSDK sdk;
    private Client client;
    private Amop amop;
    private int timeout;

    // topic control contract in nowSupport
    private TopicController topicController;

    // topic contract in nowSupport
    private Topic topic;

    // topic info list in local memory, some fields may be expired
    private final Map<String, TopicInfo> topicInfo = new ConcurrentHashMap<>();

    // history topic, (address <-> Contract)
    private final Map<String, Contract> historyTopicContract = new ConcurrentHashMap<>();

    // history topic, (address <-> version)
    private final Map<String, Long> historyTopicVersion = new ConcurrentHashMap<>();

    public FiscoBcos2(FiscoConfig fiscoConfig) throws BrokerException {
        this.fiscoConfig = fiscoConfig;
        this.sdk = Web3SDKConnector.buidBcosSDK(fiscoConfig);
        this.amop = this.sdk.getAmop();
    }

    public Amop getAmop() {
        return this.amop;
    }

    public void init(Integer groupId) throws BrokerException {
        log.info("WeEvent support solidity version, now: {} support: {}", SupportedVersion.nowVersion, SupportedVersion.history);
        this.client = Web3SDKConnector.initClient(this.sdk, groupId, this.fiscoConfig);

        if (this.topicController == null) {
            this.timeout = this.fiscoConfig.getWeEventCoreConfig().getTimeout();

            CRUDAddress crudAddress = new CRUDAddress(this.client);
            Map<Long, String> addresses = crudAddress.listAddress();
            log.info("address list in CRUD: {}", addresses);

            if (addresses.isEmpty() || !addresses.containsKey(SupportedVersion.nowVersion)) {
                log.error("no topic control[nowVersion: {}] address in CRUD, please deploy it first", SupportedVersion.nowVersion);
                throw new BrokerException(ErrorCode.TOPIC_CONTROLLER_IS_NULL);
            }

            for (Map.Entry<Long, String> controlAddress : addresses.entrySet()) {
                log.info("init topic control {} -> {}", controlAddress.getKey(), controlAddress.getValue());

                ImmutablePair<Contract, Contract> contracts = SupportedVersion.loadTopicControlContract(
                        this.client, controlAddress.getValue(), controlAddress.getKey().intValue(), this.timeout);
                this.historyTopicContract.put(contracts.right.getContractAddress(), contracts.right);
                this.historyTopicVersion.put(contracts.right.getContractAddress(), controlAddress.getKey());

                // publish and admin function use the nowVersion
                if (controlAddress.getKey().equals(SupportedVersion.nowVersion)) {
                    log.info("detect topic control in now version: {}", SupportedVersion.nowVersion);

                    this.topicController = (TopicController) contracts.left;
                    this.topic = (Topic) contracts.right;
                }
            }

            log.info("all supported solidity version: {}", this.historyTopicVersion);
        }
    }

    public void setListener(FiscoBcosDelegate.IBlockEventListener listener) {
        Web3SDK2Wrapper.setBlockNotifyCallBack(this.sdk, listener);
    }

    public List<String> listGroupId() {
        return Web3SDKConnector.listGroupId(this.client);
    }

    /*
     * Gets the contract service.
     *
     * @param contractAddress the contract address
     * @param cls the class
     * @return the contract service
     */
    protected Contract getContractService(String contractAddress, Class<?> cls) throws BrokerException {
        if (this.sdk == null || this.client == null) {
            log.error("init web3sdk failed");
            throw new BrokerException(ErrorCode.WEB3SDK_INIT_ERROR);
        }

        if (StringUtils.isBlank(contractAddress)) {
            String msg = "load contract failed, " + cls.getSimpleName();
            log.error(msg);
            throw new BrokerException(ErrorCode.LOAD_CONTRACT_ERROR);
        }

        return Web3SDK2Wrapper.loadContract(contractAddress, this.client, cls);
    }

    public boolean isTopicExist(String topicName) throws BrokerException {
        Optional<TopicInfo> topicInfo = getTopicInfo(topicName, false);
        return topicInfo.isPresent();
    }

    public boolean createTopic(String topicName) throws BrokerException {
        // check if topic contract exist
        if (isTopicExist(topicName)) {
            log.info("topic name already exist, {}", topicName);
            throw new BrokerException(ErrorCode.TOPIC_ALREADY_EXIST);
        }

        TransactionReceipt transactionReceipt = this.topicController.addTopicInfo(topicName);
        if (!transactionReceipt.isStatusOK()) {
            log.error("addTopicInfo failed due to transaction execution error");
            throw new BrokerException(ErrorCode.TRANSACTION_EXECUTE_ERROR);
        }

        Boolean result = this.topicController.getAddTopicInfoOutput(transactionReceipt).getValue1();
        if (!result) {
            log.info("topic name already exist, {}", topicName);
            throw new BrokerException(ErrorCode.TOPIC_ALREADY_EXIST);
        }

        return true;
    }

    public ListPage<String> listTopicName(Integer pageIndex, Integer pageSize) throws BrokerException {
        try {
            ListPage<String> listPage = new ListPage<>();
            Tuple3<BigInteger, BigInteger, List<String>> result = this.topicController.listTopicName(BigInteger.valueOf(pageIndex),
                    BigInteger.valueOf(pageSize));
            if (result == null) {
                log.error("TopicController.listTopicName result is empty");
                throw new BrokerException(ErrorCode.TRANSACTION_EXECUTE_ERROR);
            }
            listPage.setPageIndex(pageIndex);
            listPage.setTotal(result.getValue1().intValue());
            listPage.setPageSize(result.getValue2().intValue());
            listPage.setPageData(result.getValue3());
            return listPage;
        } catch (ContractException e) {
            log.error("listTopicName failed due to web3sdk rpc error.", e);
            throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
        }
    }

    public Optional<TopicInfo> getTopicInfo(String topicName, boolean skipCache) throws BrokerException {
        if (!skipCache && this.topicInfo.containsKey(topicName)) {
            return Optional.of(this.topicInfo.get(topicName));
        }

        try {
            Tuple8<Boolean, String, BigInteger, BigInteger, BigInteger, BigInteger, BigInteger, String> topic =
                    this.topicController.getTopicInfo(topicName);
            if (topic == null) {
                log.error("TopicController.getTopicInfo result is empty");
                throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
            }

            if (!topic.getValue1()) {
                log.info("topic not exist, {}", topicName);
                return Optional.empty();
            }

            TopicInfo topicInfo = new TopicInfo();
            topicInfo.setTopicName(topicName);
            topicInfo.setSenderAddress(topic.getValue2());
            topicInfo.setCreatedTimestamp(topic.getValue3().longValue());
            topicInfo.setSequenceNumber(topic.getValue5().longValue());
            topicInfo.setBlockNumber(topic.getValue6().longValue());
            topicInfo.setLastTimestamp(topic.getValue7().longValue());

            this.topicInfo.put(topicName, topicInfo);
            return Optional.of(topicInfo);
        } catch (ContractException e) {
            log.error("getTopicInfo failed due to web3sdk rpc error.", e);
            throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
        }
    }

    public WeEvent getEvent(String eventId) throws BrokerException {
        ParamCheckUtils.validateEventId("", eventId, getBlockHeight());

        Long blockNum = DataTypeUtils.decodeBlockNumber(eventId);
        List<WeEvent> events = this.loop(BigInteger.valueOf(blockNum));
        for (WeEvent event : events) {
            if (eventId.equals(event.getEventId())) {
                log.info("event:{}", event);
                return event;
            }
        }

        throw new BrokerException(ErrorCode.EVENT_ID_NOT_EXIST);
    }

    public CompletableFuture<SendResult> publishEvent(String topicName, String eventContent, String extensions) throws BrokerException {
        if (!isTopicExist(topicName)) {
            throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
        }

        log.info("publish async ...");
        StopWatch sw = StopWatch.createStarted();
        CompletableFuture<SendResult> future = new CompletableFuture<>();
        this.topic.publishWeEvent(topicName, eventContent, extensions, new TransactionCallback() {
            @Override
            public void onResponse(TransactionReceipt receipt) {
                SendResult sendResult = new SendResult();
                sendResult.setTopic(topicName);

                // success
                if (receipt.isStatusOK()) {
                    Tuple1<BigInteger> result = topic.getPublishWeEventOutput(receipt);
                    int sequence = result.getValue1().intValue();
                    if (sequence == 0) {
                        log.error("permission forbid to publish event");
                        sendResult.setStatus(SendResult.SendResultStatus.NO_PERMISSION);
                    } else {
                        sendResult.setStatus(SendResult.SendResultStatus.SUCCESS);
                        sendResult.setEventId(DataTypeUtils.encodeEventId(topicName, Numeric.decodeQuantity(receipt.getBlockNumber()).intValue(), sequence));
                    }
                } else { // error
                    try {
                        RetCode retCode = ReceiptParser.parseTransactionReceipt(receipt);
                        if (retCode.getCode() == TransactionReceiptStatus.TimeOut.getCode()) {
                            log.error("publish event failed due to transaction execution timeout. {}", retCode.toString());
                            sendResult.setStatus(SendResult.SendResultStatus.TIMEOUT);
                        } else {
                            log.error("publish event failed due to transaction execution error. {}", retCode.toString());
                            sendResult.setStatus(SendResult.SendResultStatus.ERROR);
                        }
                    } catch (ContractException exception) {
                        log.error("publish event failed due to transaction execution error. {}, {}", exception.getErrorCode(), exception.getMessage());
                        sendResult.setStatus(SendResult.SendResultStatus.ERROR);
                    }
                }

                sw.stop();
                log.info("publish async result, {} cost: {} ms", sendResult, sw.getTime());
                future.complete(sendResult);
            }
        });
        return future;
    }

    public CompletableFuture<SendResult> sendRawTransaction(String topicName, String transactionHex) {
        return CompletableFuture.supplyAsync(() -> {
            SendTransaction sendTransaction = this.client.sendRawTransaction(transactionHex);

            SendResult sendResult = new SendResult();
            sendResult.setTopic(topicName);

            Optional<TransactionReceipt> receiptOptional = getTransactionReceiptRequest(sendTransaction.getTransactionHash());
            if (receiptOptional.isPresent()) {
                List<TypeReference<?>> referencesList = Collections.singletonList(new TypeReference<Uint256>() {
                });
                List<Type> returnList = FunctionReturnDecoder.decode(
                        String.valueOf(receiptOptional.get().getOutput()),
                        Utils.convert(referencesList));

                int sequence = ((BigInteger) returnList.get(0).getValue()).intValue();
                if (sequence == 0) {
                    log.error("this FISCO-BCOS account has no permission to publish event");
                    sendResult.setStatus(SendResult.SendResultStatus.NO_PERMISSION);
                } else {
                    sendResult.setStatus(SendResult.SendResultStatus.SUCCESS);
                    sendResult.setEventId(DataTypeUtils.encodeEventId(topicName,
                            Numeric.decodeQuantity(receiptOptional.get().getBlockNumber()).intValue(),
                            sequence));
                }
            } else {
                sendResult.setStatus(SendResult.SendResultStatus.ERROR);
            }

            return sendResult;
        });
    }

    /**
     * Get a TransactionReceipt request from a transaction Hash.
     *
     * @param transactionHash the transactionHash value
     * @return the transactionReceipt wrapper
     */
    private Optional<TransactionReceipt> getTransactionReceiptRequest(String transactionHash) {
        Optional<TransactionReceipt> receiptOptional = Optional.empty();

        try {
            for (int i = 0; i < WeEventConstants.POLL_TRANSACTION_ATTEMPTS; i++) {
                receiptOptional = this.client.getTransactionReceipt(transactionHash).getTransactionReceipt();
                if (!receiptOptional.isPresent()) {
                    Thread.sleep(this.fiscoConfig.getWeEventCoreConfig().getConsumerIdleTime());
                } else {
                    return receiptOptional;
                }
            }
        } catch (InterruptedException e) {
            log.error("get transactionReceipt failed.", e);
            Thread.currentThread().interrupt();
        }
        return receiptOptional;
    }

    /*
     * getBlockHeight
     *
     * @return 0L if net error
     */
    public Long getBlockHeight() throws BrokerException {
        return Web3SDK2Wrapper.getBlockHeight(this.client);
    }

    /*
     * Fetch all event in target block.
     *
     * @param blockNum the blockNum
     * @return java.lang.Integer null if net error
     */
    public List<WeEvent> loop(BigInteger blockNum) throws BrokerException {
        return Web3SDK2Wrapper.loop(this.client, blockNum, this.historyTopicVersion, this.historyTopicContract);
    }

    public GroupGeneral getGroupGeneral() throws BrokerException {
        return Web3SDK2Wrapper.getGroupGeneral(this.client);
    }

    public ListPage<TbTransHash> queryTransList(String transHash, BigInteger blockNumber, Integer pageIndex, Integer pageSize) throws BrokerException {
        return Web3SDK2Wrapper.queryTransList(this.client, transHash, blockNumber, pageIndex, pageSize);
    }

    public ListPage<TbBlock> queryBlockList(String transHash, BigInteger blockNumber, Integer pageIndex, Integer pageSize) throws BrokerException {
        return Web3SDK2Wrapper.queryBlockList(this.client, transHash, blockNumber, pageIndex, pageSize);
    }

    public ListPage<TbNode> queryNodeList() throws BrokerException {
        return Web3SDK2Wrapper.queryNodeList(this.client);
    }

    public ContractContext getContractContext() {
        ContractContext contractContext = new ContractContext();
        contractContext.setGasLimit(Web3SDK2Wrapper.gasProvider.getGasLimit().longValue());
        contractContext.setGasPrice(Web3SDK2Wrapper.gasProvider.getGasPrice().longValue());
        contractContext.setTopicAddress(this.topic.getContractAddress());
        contractContext.setBlockNumber(client.getBlockLimit().subtract(GroupManagerService.BLOCK_LIMIT).longValue());
        contractContext.setBlockLimit(client.getBlockLimit().longValue());
        contractContext.setChainId(Web3SDKConnector.chainID);

        return contractContext;
    }

    public boolean addOperator(String topicName, String operatorAddress) throws BrokerException {
        if (!isTopicExist(topicName)) {
            throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
        }

        TransactionReceipt transactionReceipt;
        try {
            transactionReceipt = this.topic.addOperator(topicName, operatorAddress);
        } catch (Exception e) {
            log.error("addOperator failed due to web3sdk rpc timeout.", e);
            throw new BrokerException(ErrorCode.TRANSACTION_TIMEOUT);
        }

        if (!transactionReceipt.isStatusOK()) {
            log.error("Topic.addOperator transactionReceipt is empty.");
            throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
        }

        List<TypeReference<?>> referencesList = Collections.singletonList(new TypeReference<Uint32>() {
        });
        List<Type> returnList = FunctionReturnDecoder.decode(
                String.valueOf(transactionReceipt.getOutput()),
                Utils.convert(referencesList));

        int code = ((BigInteger) returnList.get(0).getValue()).intValue();
        if (code == ErrorCode.NO_PERMISSION.getCode()) {
            log.error("Topic.addOperator, this FISCO-BCOS account has no permission to add operator");
            throw new BrokerException(ErrorCode.NO_PERMISSION);
        } else if (code == ErrorCode.OPERATOR_ALREADY_EXIST.getCode()) {
            log.error("Topic.addOperator, operator :{} already exists", operatorAddress);
            throw new BrokerException(ErrorCode.OPERATOR_ALREADY_EXIST);
        } else {
            return true;
        }
    }

    public boolean delOperator(String topicName, String operatorAddress) throws BrokerException {
        if (!isTopicExist(topicName)) {
            throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
        }

        TransactionReceipt transactionReceipt;
        try {
            transactionReceipt = this.topic.delOperator(topicName, operatorAddress);
        } catch (Exception e) {
            log.error("delOperator failed due to web3sdk rpc timeout.", e);
            throw new BrokerException(ErrorCode.TRANSACTION_TIMEOUT);
        }

        if (!transactionReceipt.isStatusOK()) {
            log.error("Topic.delOperator transactionReceipt is empty.");
            throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
        }

        List<TypeReference<?>> referencesList = Collections.singletonList(new TypeReference<Uint32>() {
        });
        List<Type> returnList = FunctionReturnDecoder.decode(
                String.valueOf(transactionReceipt.getOutput()),
                Utils.convert(referencesList));
        int code = ((BigInteger) returnList.get(0).getValue()).intValue();

        if (code == ErrorCode.NO_PERMISSION.getCode()) {
            log.error("Topic.delOperator, this FISCO-BCOS account has no permission to add operator");
            throw new BrokerException(ErrorCode.NO_PERMISSION);
        } else if (code == ErrorCode.OPERATOR_NOT_EXIST.getCode()) {
            log.error("Topic.delOperator, operator :{} not exists", operatorAddress);
            throw new BrokerException(ErrorCode.OPERATOR_NOT_EXIST);
        } else {
            return true;
        }
    }

    public List<String> listOperator(String topicName) throws BrokerException {
        if (!isTopicExist(topicName)) {
            throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
        }

        Tuple2<BigInteger, List<String>> tuple2;
        try {
            tuple2 = this.topic.listOperator(topicName);
        } catch (ContractException e) {
            log.error("query operator list failed due to web3sdk rpc error.", e);
            throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
        }

        if (tuple2 == null) {
            log.error("Topic.listOperator result is empty");
            throw new BrokerException(ErrorCode.WEB3SDK_RPC_ERROR);
        }

        int code = tuple2.getValue1().intValue();
        if (code == ErrorCode.NO_PERMISSION.getCode()) {
            log.error("Topic.listOperator, this FISCO-BCOS account has no permission to query operator list");
            throw new BrokerException(ErrorCode.NO_PERMISSION);
        }

        return tuple2.getValue2();
    }

    public CompletableFuture<SendResult> sendAMOP(String topicName, String content) {
        AmopMsgOut out = new AmopMsgOut();
        out.setTopic(topicName);
        out.setTimeout(6000L);
        out.setContent(content.getBytes());

        StopWatch sw = StopWatch.createStarted();
        CompletableFuture<SendResult> future = new CompletableFuture<>();
        AmopResponseCallback callback = new AmopResponseCallback() {
            @Override
            public void onResponse(AmopResponse response) {
                sw.stop();
                log.info("receive amop response, id: {} result: {}-{} cost: {}", response.getMessageID(), response.getErrorCode(), response.getErrorMessage(), sw.getTime());
                SendResult sendResult = new SendResult();
                sendResult.setTopic(topicName);
                sendResult.setEventId(response.getMessageID());

                if (response.getErrorCode() == 0) {
                    sendResult.setStatus(SendResult.SendResultStatus.SUCCESS);
                } else {
                    sendResult.setStatus(SendResult.SendResultStatus.ERROR);
                }
                future.complete(sendResult);
            }
        };
        this.amop.sendAmopMsg(out, callback);
        return future;
    }
}
