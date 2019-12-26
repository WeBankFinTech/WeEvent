package com.webank.weevent.governance.utils;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.webank.weevent.governance.entity.RuleEngineConditionEntity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.MapLikeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;


@Slf4j
public class JsonUtil {


    private static ObjectMapper objectMapper = new ObjectMapper();

    static {
        // Include.NON_NULL Property is NULL and not serialized
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        //Do not convert inconsistent fields
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static <T> String toJSONString(T data) throws IOException {
        Assert.notNull(data, "data is null");
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            log.error("conversion of Json failed", e);
            throw new IOException("conversion of Json failed", e);
        }
    }

    public static <T> T parseObject(String data, Class<T> tClass) throws IOException {
        Assert.hasText(data, "data without text");
        try {
            return objectMapper.readValue(data, tClass);
        } catch (JsonProcessingException e) {
            log.error("conversion of Json failed", e);
            throw new IOException("conversion of Json failed", e);
        }
    }


    public static <T, R> Map<T, R> parseObjectToMap(String data, Class tclass1, Class tclass2) throws IOException {
        Assert.hasText(data, "data without text");
        try {
            MapLikeType mapLikeType = objectMapper.getTypeFactory().constructMapLikeType(Map.class, tclass1, tclass2);
            return objectMapper.readValue(data, mapLikeType);
        } catch (Exception e) {
            log.error("conversion of Json failed", e);
            throw new IOException("conversion of Json failed", e);
        }
    }

    public static <T> List<T> json2List(String jsonString) throws IOException {
        try {
            if (jsonString == null) {
                return null;
            }
            return objectMapper.readValue(jsonString, new TypeReference<List<RuleEngineConditionEntity>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("convert jsonString to List failed ", e);
            throw new IOException("conversion of List failed", e);
        }
    }

}
