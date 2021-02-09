package com.webank.weevent.core.config;


import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Setter
@ToString
@Component
public class WeEventCoreConfig {
    private String version;

    private String orgId;

    private Integer timeout;

    private Integer poolSize;

    private Integer maxPoolSize;

    private Integer keepAliveSeconds;

    private String web3sdkEncryptType;

    private Integer consumerIdleTime;

    private Integer consumerHistoryMergeBlock;
}
