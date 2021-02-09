package com.webank.weevent.core.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import com.webank.weevent.core.JUnitTestBase;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.config.ConfigOption;
import org.fisco.bcos.sdk.config.exceptions.ConfigException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * FiscoConfig Tester.
 *
 * @author matthewliu
 * @version 1.0
 * @since 03/01/2020
 */
@Slf4j
public class FiscoConfigTest extends JUnitTestBase {


    @Before
    public void before() {
        log.info("=============================={}.{}==============================",
                this.getClass().getSimpleName(),
                this.testName.getMethodName());
    }

    /**
     * test load
     */
    @Test
    public void testLoadDefault() throws IOException, ConfigException {
        FiscoConfig fiscoConfig = new FiscoConfig();
        Assert.assertTrue(fiscoConfig.load(""));
        // default value is empty
        Assert.assertTrue(fiscoConfig.getConfigProperty().getNetwork().size()>0);

        ConfigOption configOption = new ConfigOption(fiscoConfig.getConfigProperty());
        log.info("getAccountConfig:{}", configOption.getAccountConfig().toString());
        log.info("getNetworkConfig:{}", configOption.getNetworkConfig().getPeers());
        log.info("getCaCertPath:{}", configOption.getCryptoMaterialConfig().getCaCertPath());
        log.info("getAmopTopicConfig:{}", configOption.getAmopConfig().getAmopTopicConfig());
    }


    /**
     * test load
     */
    @Test
    public void testLoad(){
        FiscoConfig fiscoConfig = new FiscoConfig();
        Assert.assertTrue(fiscoConfig.load("classpath:fisco.yml"));

        System.out.println(fiscoConfig.getConfigProperty().getNetwork().size());

        // default value is empty
        Assert.assertTrue(fiscoConfig.getConfigProperty().getNetwork().size()>0);
    }
}

