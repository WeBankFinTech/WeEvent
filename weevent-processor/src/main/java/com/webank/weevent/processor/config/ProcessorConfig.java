package com.webank.weevent.processor.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Slf4j
@Setter
@Getter
@ToString
@Component
@PropertySource(value = "classpath:processor.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class ProcessorConfig {

    @Value("${org.quartz.scheduler.instanceName:}")
    private String schedulerInstanceName;
}