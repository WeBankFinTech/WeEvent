package com.webank.weevent.st;

import com.webank.weevent.JUnitTestBase;
import com.webank.weevent.sdk.ErrorCode;
import com.webank.weevent.sdk.SendResult;
import com.webank.weevent.sdk.TopicInfo;
import com.webank.weevent.sdk.TopicPage;
import com.webank.weevent.sdk.WeEvent;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

@Slf4j
public class RestfullTest extends JUnitTestBase {

    private String url;
    private RestTemplate rest = null;
    private String eventId = "";
    private String restTopic = "com.weevent.test";
    private String content = "hello restful";

    @Before
    public void before() {
        url = "http://localhost:" + listenPort + "/weevent/rest/";
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        this.rest = new RestTemplate(requestFactory);
        rest.getForEntity(url + "open?topic={topic}", Boolean.class, this.restTopic);

        eventId = rest.getForEntity(url + "publish?topic={topic}&content={content}", SendResult.class, this.restTopic,
                this.content).getBody().getEventId();
    }

    @Test
    public void testOpenNoGroupId() {
        ResponseEntity<Boolean> rsp = rest.getForEntity(url + "open?topic={topic}", Boolean.class, this.restTopic);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody());
    }

    @Test
    public void testOpenWithGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<Boolean> rsp = rest.getForEntity(url + "open?topic={topic}&groupId={groupId}", Boolean.class,
                this.restTopic, WeEvent.DEFAULT_GROUP_ID);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody());
    }

    @Test
    public void testCloseNoGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<Boolean> rsp = rest.getForEntity(url + "close?topic={topic}", Boolean.class, this.restTopic);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody());
    }

    @Test
    public void testCloseCotainGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<Boolean> rsp = rest.getForEntity(url + "close?topic={topic}&groupId={groupId}", Boolean.class,
                this.restTopic, WeEvent.DEFAULT_GROUP_ID);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody());
    }

    @Test
    public void testExistNoGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<Boolean> rsp = rest.getForEntity(url + "exist?topic={topic}", Boolean.class, this.restTopic);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody());
    }

    @Test
    public void testExistCotainGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<Boolean> rsp = rest.getForEntity(url + "exist?topic={topic}&groupId={groupId}", Boolean.class,
                this.restTopic, WeEvent.DEFAULT_GROUP_ID);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody());
    }

    @Test
    public void testStateNoGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<TopicInfo> rsp = rest.getForEntity(url + "state?topic={topic}", TopicInfo.class, this.restTopic);
        System.out.println("state, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertEquals(this.restTopic, rsp.getBody().getTopicName());
    }

    @Test
    public void testStateWithGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<TopicInfo> rsp = rest.getForEntity(url + "state?topic={topic}&groupId={groupId}",
                TopicInfo.class, this.restTopic, WeEvent.DEFAULT_GROUP_ID);
        System.out.println("state, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertEquals(this.restTopic, rsp.getBody().getTopicName());
    }

    @Test
    public void testListNoGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<TopicPage> rsp = rest.getForEntity(url + "list?pageIndex={pageIndex}&pageSize={pageSize}",
                TopicPage.class, "0", "10");
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getTotal() > 0);
    }

    @Test
    public void testListWithGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<TopicPage> rsp = rest.getForEntity(
                url + "list?pageIndex={pageIndex}&pageSize={pageSize}&groupId={groupId}", TopicPage.class, "0", "10",
                WeEvent.DEFAULT_GROUP_ID);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getTotal() > 0);
    }

    @Test
    public void testGetEventNoGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<WeEvent> rsp = rest.getForEntity(url + "getEvent?eventId={eventId}", WeEvent.class,
                this.eventId);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        WeEvent event = rsp.getBody();
        String returnContent = new String(event.getContent());
        Assert.assertEquals(this.content, returnContent);
    }

    @Test
    public void testGetEventWithGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<WeEvent> rsp = rest.getForEntity(url + "getEvent?eventId={eventId}&groupId={groupId}",
                WeEvent.class, this.eventId, WeEvent.DEFAULT_GROUP_ID);
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        WeEvent event = rsp.getBody();
        String returnContent = new String(event.getContent());
        Assert.assertEquals(this.content, returnContent);
    }

    @Test
    public void testPublishNoGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<SendResult> rsp = rest.getForEntity(url + "publish?topic={topic}&content={content}",
                SendResult.class, this.restTopic, this.content);
        log.info("publish, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getEventId().contains("-"));
    }

    @Test
    public void testPublishContainGroupId() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<SendResult> rsp = rest.getForEntity(
                url + "publish?topic={topic}&content={content}&groupId={groupId}", SendResult.class, this.restTopic,
                this.content, WeEvent.DEFAULT_GROUP_ID);
        log.info("publish, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getEventId().contains("-"));
    }

    @Test
    public void testPublishContentIs10K() {
        log.info("===================={}", this.testName.getMethodName());

        MultiValueMap<String, String> eventData = new LinkedMultiValueMap<>();
        eventData.add("topic", this.restTopic);
        eventData.add("content", get10KStr());

        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.APPLICATION_FORM_URLENCODED;
        headers.setContentType(type);
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(eventData, headers);
        ResponseEntity<SendResult> rsp = rest.postForEntity(url + "publish", request, SendResult.class);
        log.info("publish, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getEventId().contains("-"));
    }

    @Test
    public void testPublishContentGt10K() {
        log.info("===================={}", this.testName.getMethodName());

        MultiValueMap<String, String> eventData = new LinkedMultiValueMap<>();
        eventData.add("topic", this.restTopic);
        eventData.add("content", get10KStr() + "s");

        HttpHeaders headers = new HttpHeaders();
        MediaType type = MediaType.APPLICATION_FORM_URLENCODED;
        headers.setContentType(type);
        headers.add("Accept", MediaType.APPLICATION_JSON.toString());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(eventData, headers);
        ResponseEntity<String> rsp = rest.postForEntity(url + "publish", request, String.class);
        log.info("publish, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().contains(ErrorCode.EVENT_CONTENT_EXCEEDS_MAX_LENGTH.getCode() + ""));
    }

    @Test
    public void testPublishNoGroupIdContainExt() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<SendResult> rsp = rest.getForEntity(
                url + "publish?topic={topic}&content={content}&weevent-test1={value1}&weevent-test2={value2}",
                SendResult.class, this.restTopic, this.content, "test1value", "test2vlaue");
        log.info("publish, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getEventId().contains("-"));
    }

    @Test
    public void testPublishContainGroupIdContainExt() {
        log.info("===================={}", this.testName.getMethodName());

        ResponseEntity<SendResult> rsp = rest.getForEntity(url
                        + "publish?topic={topic}&content={content}&weevent-test1={value1}&weevent-test2={value2}&groupId={groupId}",
                SendResult.class, this.restTopic, this.content, "test1value", "test2vlaue", WeEvent.DEFAULT_GROUP_ID);
        log.info("publish, status: " + rsp.getStatusCode() + " body: " + rsp.getBody());
        Assert.assertTrue(rsp.getStatusCodeValue() == 200);
        Assert.assertTrue(rsp.getBody().getEventId().contains("-"));
    }

    // get 10K string
    private String get10KStr() {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < 1024; i++) {
            result.append("abcdefghij");
        }
        return result.toString();
    }
}
