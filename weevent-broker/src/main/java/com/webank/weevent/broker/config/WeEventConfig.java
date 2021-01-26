package com.webank.weevent.broker.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;


/**
 * WeEvent Config that auto loaded by spring ApplicationContext
 *
 * @author matthewliu
 * @version 1.0
 * @since 2019/1/28
 */
@Slf4j
@Getter
@Setter
@ToString
@Component
@PropertySource(value = "classpath:weevent.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class WeEventConfig {
    @Value("${ip.check.white-list}")
    private String ipWhiteList;

    @Value("${lru.cache.capacity}")
    private Integer maxCapacity;

    @Value("${stomp.heartbeats}")
    private Integer stompHeartbeats;

    @Value("${mqtt.broker.tcp.port}")
    private Integer mqttTcpPort;

    @Value("${mqtt.broker.keepalive}")
    private Integer keepAlive;
}
