package weevent.robust.service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;


@Slf4j
@Component
public class InitialService implements ApplicationRunner {


    private final List<String> subIdList = new ArrayList<>();

    public final static String SUBSCRIBE_ID = "subscribeId";

    private final static String HTTP_HEADER = "http://";


    @Value("${weevent.broker.url}")
    private String url;

    @Value("${statistic.file.path}")
    private String statisticFilePath;

    @Value("${subscripId.file.path}")
    private String subscripIdPath;

    @Autowired
    private RestTemplate restTemplate;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        File subIdFile = new File(subscripIdPath);
        StringBuffer bufferUrl;
        StringBuffer callBackBufferUrl;

        // subscribe rest topic,  then callback
        bufferUrl = ScheduledService.getIntegralUrl(HTTP_HEADER, url, "/weevent/rest/subscribe?topic={topic}&subscriptionId={subscriptionId}&url={url}");
        callBackBufferUrl = ScheduledService.getIntegralUrl(HTTP_HEADER, url, "/weevent/mock/rest/onEvent");
        ResponseEntity<String> rsp = restTemplate.getForEntity(bufferUrl.toString(), String.class, "com.weevent.rest", "", callBackBufferUrl.toString());
        String subId = rsp.getBody();
        log.info("rest subId: " + subId);
        subIdList.add(subId);

        // subscribe  mqtt topic then callback
        bufferUrl = ScheduledService.getIntegralUrl(HTTP_HEADER, url, "/weevent/rest/subscribe?topic={topic}&subscriptionId={subscriptionId}&url={url}");
        callBackBufferUrl = ScheduledService.getIntegralUrl(HTTP_HEADER, url, "/weevent/mock/onEventMqtt");
        rsp = restTemplate.getForEntity(bufferUrl.toString(), String.class, "com.weevent.mqtt", "", callBackBufferUrl.toString());
        subId = rsp.getBody();
        log.info("mqtt subId: " + subId);
        subIdList.add(subId);

        //Convert 'subIdList' to Json format
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(SUBSCRIBE_ID, subIdList);
        log.info(jsonObject.toJSONString());
        ScheduledService.writeStringToFile(subIdFile.getAbsolutePath(), jsonObject.toJSONString(), true);
    }


}
