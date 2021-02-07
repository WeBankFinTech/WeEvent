package com.webank.weevent.broker;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;

import com.webank.weevent.broker.config.WeEventConfig;
import com.webank.weevent.broker.filter.HttpInterceptorConfig;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FabricConfig;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.fabric.FabricBroker4Consumer;
import com.webank.weevent.core.fabric.FabricBroker4Producer;
import com.webank.weevent.core.fabric.sdk.FabricDelegate;
import com.webank.weevent.core.fisco.FiscoBcosBroker4Consumer;
import com.webank.weevent.core.fisco.FiscoBcosBroker4Producer;
import com.webank.weevent.core.fisco.web3sdk.FiscoBcosDelegate;

import com.fasterxml.jackson.databind.JsonNode;
import com.googlecode.jsonrpc4j.ErrorResolver;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImplExporter;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;


/**
 * Main entrance for broker application.
 *
 * @author matthewliu
 * @since 2018/11/21
 */
@Slf4j
@EnableDiscoveryClient
@SpringBootApplication(scanBasePackages = {"com.webank.weevent.broker", "com.webank.weevent.core.config"})
public class BrokerApplication {
    public static void main(String[] args) {
        /* Forbid banner.
        SpringApplicationBuilder builder = new SpringApplicationBuilder(BrokerApplication.class);
        builder.bannerMode(Banner.Mode.OFF).run(args);
        */
        SpringApplication app = new SpringApplication(BrokerApplication.class);
        app.addListeners(new ApplicationPidFileWriter());
        app.run();

        log.info("start broker success");
    }

    // tomcat configuration to enhance performance
    @Bean
    public ConfigurableServletWebServerFactory configurableServletWebServerFactory() {
        log.info("custom TomcatServletWebServerFactory");

        TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory();
        factory.setProtocol(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);

        factory.addConnectorCustomizers((connector) -> {
                    // default max connections = 10000
                    // default connection timeout = 20000
                    // default max accept count = 100
                    // default max worker thread = 200
                    connector.setEnableLookups(false);
                    connector.setAllowTrace(false);

                    Http11NioProtocol http11NioProtocol = (Http11NioProtocol) connector.getProtocolHandler();
                    http11NioProtocol.setKeepAliveTimeout(60000);
                    http11NioProtocol.setMaxKeepAliveRequests(10000);
                    http11NioProtocol.setDisableUploadTimeout(true);
                    http11NioProtocol.setTcpNoDelay(true);
                }
        );

        return factory;
    }

    // support CORS
    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedHeaders("*")
                        .allowedMethods("*");
                        //.allowedOrigins("*")
            }
        };
    }

    // export jsonrpc4j
    @Bean
    public AutoJsonRpcServiceImplExporter autoJsonRpcServiceImplExporter() {
        //in here you can provide custom HTTP status code providers etc. eg:
        //exporter.setHttpStatusCodeProvider();

        AutoJsonRpcServiceImplExporter exporter = new AutoJsonRpcServiceImplExporter();
        exporter.setErrorResolver((Throwable t, Method method, List<JsonNode> arguments) -> {
            log.error("Exception in json rpc invoke", t);

            int code = ErrorResolver.JsonError.ERROR_NOT_HANDLED.code;
            if (t instanceof BrokerException) {
                code = ((BrokerException) t).getCode();
            }
            return new ErrorResolver.JsonError(code, t.getMessage(), null);
        });

        return exporter;
    }

    // FiscoBcosDelegate
    @Bean
    @ConditionalOnProperty(prefix = "block.chain", name = "type", havingValue = "fisco")
    public FiscoBcosDelegate fiscoBcosDelegate(FiscoConfig fiscoConfig) throws BrokerException {
        log.info("++++++++++ FISCO-BCOS Enter ++++++++++");
        fiscoConfig.load("");
        FiscoBcosDelegate fiscoBcosDelegate = new FiscoBcosDelegate();
        fiscoBcosDelegate.initProxy(fiscoConfig);
        return fiscoBcosDelegate;
    }

    // FabricDelegate
    @Bean
    @ConditionalOnProperty(prefix = "block.chain", name = "type", havingValue = "fabric")
    public FabricDelegate fabricDelegate(FabricConfig fabricConfig) throws BrokerException {
        log.info("++++++++++ Fabric Enter ++++++++++");
        FabricDelegate fabricDelegate = new FabricDelegate();
        fabricDelegate.initProxy(fabricConfig);

        return fabricDelegate;
    }

    // FISCO-BCOS IProducer
    @Bean
    @ConditionalOnBean(FiscoBcosDelegate.class)
    public IProducer fiscoIProducer(FiscoBcosDelegate fiscoBcosDelegate) {
        FiscoBcosBroker4Producer fiscoBcosBroker4Producer = new FiscoBcosBroker4Producer(fiscoBcosDelegate);
        fiscoBcosBroker4Producer.startProducer();
        return fiscoBcosBroker4Producer;
    }

    // FISCO-BCOS IConsumer
    @Bean
    @ConditionalOnBean(FiscoBcosDelegate.class)
    public IConsumer fiscoIConsumer(FiscoBcosDelegate fiscoBcosDelegate) throws BrokerException {
        FiscoBcosBroker4Consumer fiscoBcosBroker4Consumer = new FiscoBcosBroker4Consumer(fiscoBcosDelegate);
        fiscoBcosBroker4Consumer.startConsumer();
        return fiscoBcosBroker4Consumer;
    }

    // Fabric IProducer
    @Bean
    @ConditionalOnBean(FabricDelegate.class)
    public IProducer fabricIProducer(FabricDelegate fabricDelegate) {
        FabricBroker4Producer fabricBroker4Producer = new FabricBroker4Producer(fabricDelegate);
        fabricBroker4Producer.startProducer();
        return fabricBroker4Producer;
    }

    // Fabric IConsumer
    @Bean
    @ConditionalOnBean(FabricDelegate.class)
    public IConsumer fabricIConsumer(FabricDelegate fabricDelegate) throws BrokerException {
        FabricBroker4Consumer fabricBroker4Consumer = new FabricBroker4Consumer(fabricDelegate);
        fabricBroker4Consumer.startConsumer();
        return fabricBroker4Consumer;
    }

    // http filter
    @Bean
    public HttpInterceptorConfig interceptorConfig(WeEventConfig weEventConfig) {
        HttpInterceptorConfig config = new HttpInterceptorConfig();
        config.setWeEventConfig(weEventConfig);
        return config;
    }


    // Notice: json rpc need default bean org.springframework.scheduling.TaskScheduler(name = "taskScheduler")
    // daemon thread pool
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor pool = new ThreadPoolTaskExecutor();
        pool.setThreadNamePrefix("weevent-executor-");
        // run in thread immediately, no blocking queue
        pool.setQueueCapacity(0);
        pool.setDaemon(true);
        pool.initialize();

        log.info("init daemon thread pool");
        return pool;
    }
}
