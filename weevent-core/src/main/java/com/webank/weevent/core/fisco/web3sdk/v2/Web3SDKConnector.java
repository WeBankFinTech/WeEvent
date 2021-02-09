package com.webank.weevent.core.fisco.web3sdk.v2;


import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.core.config.FiscoConfig;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.fisco.bcos.sdk.BcosSDK;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.config.ConfigOption;
import org.fisco.bcos.sdk.config.exceptions.ConfigException;
import org.fisco.bcos.sdk.config.model.ConfigProperty;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.CryptoType;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Initialize client, connector to FISCO-BCOS.
 *
 * @author matthewliu
 * @since 2019/12/26
 */
@Slf4j
public class Web3SDKConnector {

    // The prefix of FISCO-BCOS version 2.X
    public static final String FISCO_BCOS_2_X_VERSION_PREFIX = "2.";

    // default group in FISCO-BCOS, already exist
    public static final String DEFAULT_GROUP_ID = "1";

    // FISCO-BCOS chain id
    public static String chainID;

    private Web3SDKConnector() {
    }

    /**
     * build BcosSDK
     *
     * @param fiscoConfig fisco properties
     * @return BcosSDK instance
     * @throws BrokerException ConfigException
     */
    public static BcosSDK buidBcosSDK(FiscoConfig fiscoConfig) throws BrokerException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        ConfigOption configOption;

        try {
            if (fiscoConfig.getWeEventCoreConfig().getWeb3sdkEncryptType().equals("ECDSA_TYPE")) {
                configOption = new ConfigOption(fiscoConfig.getConfigProperty(), CryptoType.ECDSA_TYPE);
            } else if (fiscoConfig.getWeEventCoreConfig().getWeb3sdkEncryptType().equals("SM2_TYPE")) {
                configOption = new ConfigOption(fiscoConfig.getConfigProperty(), CryptoType.SM_TYPE);
            } else {
                log.error("unknown encrypt type:{}, support ECDSA_TYPE or SM2_TYPE", fiscoConfig.getWeEventCoreConfig().getWeb3sdkEncryptType());
                throw new BrokerException(ErrorCode.BCOS_SDK_BUILD_ERROR);
            }
        } catch (ConfigException e) {
            log.error("build BcosSDK, load configOption fail", e);
            throw new BrokerException(ErrorCode.BCOS_SDK_BUILD_ERROR);
        }

        return new BcosSDK(configOption);
    }

    /*
     * initialize client handler with given BcosSDK
     *
     * @param sdk web3sdk's bcosSDK
     * @param groupId groupId
     * @return Client
     */
    public static Client initClient(BcosSDK sdk, Integer groupId, FiscoConfig fiscoConfig) throws BrokerException {
        // init client with given group id
        try {
            log.info("begin to initialize web3sdk's BcosSDK, group id: {}", groupId);
            StopWatch sw = StopWatch.createStarted();

            Client client = sdk.getClient(groupId);
            if (fiscoConfig.getWeEventCoreConfig().getWeb3sdkEncryptType().equals("ECDSA_TYPE")) {
                CryptoKeyPair keyPair = client.getCryptoSuite().getKeyPairFactory().createKeyPair((String)fiscoConfig.getConfigProperty().getAccount().get("accountAddress"));
                client.getCryptoSuite().setCryptoKeyPair(keyPair);
            }

            // check connect with getNodeVersion command
            org.fisco.bcos.sdk.model.NodeVersion version = client.getNodeVersion();
            String nodeVersion = version.getNodeVersion().getVersion();
            if (StringUtils.isBlank(nodeVersion)
                    || !nodeVersion.contains(FISCO_BCOS_2_X_VERSION_PREFIX)) {
                log.error("init web3sdk failed, mismatch FISCO-BCOS version in node: {}", nodeVersion);
                throw new BrokerException(ErrorCode.WEB3SDK_INIT_ERROR);
            }
            chainID = version.getNodeVersion().getChainId();

            sw.stop();
            log.info("initialize client success, group id: {} cost: {} ms", groupId, sw.getTime());
            return client;
        } catch (Exception e) {
            log.error("init client failed", e);
            throw new BrokerException(ErrorCode.WEB3SDK_INIT_ERROR);
        }
    }

    public static ThreadPoolTaskExecutor initThreadPool(int core, int max, int keepalive) {
        // init thread pool
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("web3sdk-");
        pool.setCorePoolSize(core);
        pool.setMaxPoolSize(max);
        // queue conflict with thread pool scale up, forbid it
        pool.setQueueCapacity(0);
        pool.setKeepAliveSeconds(keepalive);
        // abort policy
        pool.setRejectedExecutionHandler(null);
        pool.setDaemon(true);
        pool.initialize();

        log.info("init ThreadPoolTaskExecutor");
        return pool;
    }

    public static List<String> listGroupId(Client client) {
        return client.getGroupList().getGroupList();
    }
}
