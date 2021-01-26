package com.webank.weevent.governance.common;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Slf4j
@Setter
@Getter
@Component
@PropertySource(value = "classpath:governance.properties", ignoreResourceNotFound = true, encoding = "UTF-8")
public class GovernanceConfig {

    @Value("${https.read-timeout}")
    private int readTimeout;

    @Value("${https.connect-timeout}")
    private int connectTimeOut;

    @Value("${http.connect-timeout}")
    private int httpConnectTimeOut;

    // max connect
    @Value("${http.client.max-total}")
    private int maxTotal;

    @Value("${http.client.max-per-route}")
    private int maxPerRoute;

    @Value("${http.client.connection-request-timeout}")
    private int connectionRequestTimeout;

    @Value("${http.client.connection-timeout}")
    private int connectionTimeout;

    @Value("${http.client.socket-timeout}")
    private int socketTimeout;

    @Value("${jwt.private.secret}")
    private String PrivateSecret;

    @Value("${file.transport.path}")
    private String fileTransportPath;
    
    @Value("${acount.name}")
    private String acountName;
    
    @Value("${acount.passwrod}")
    private String acountPasswrod;
    
    public static String acount_name;
    public static String acount_passwrod;
    
    @PostConstruct
    private void init() {
    	acount_name = acountName;
    	acount_passwrod = acountPasswrod;
    }

}
