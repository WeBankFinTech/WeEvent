package com.webank.weevent.client;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class JsonHelper {

    private static ObjectMapper OBJECT_MAPPER;

    static {
        OBJECT_MAPPER = new ObjectMapper();

        // Include.NON_NULL Property is NULL and not serialized
        OBJECT_MAPPER.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        // DO NOT convert inconsistent fields
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }

    /**
     * convert object to String
     *
     * @param object java object
     * @return json data
     * @throws BrokerException BrokerException
     */
    public static String object2Json(Object object) throws BrokerException {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("convert object to jsonString failed ", e);
            throw new BrokerException(ErrorCode.JSON_ENCODE_EXCEPTION);
        }
    }

    /**
     * convert object to byte[]
     *
     * @param object java object
     * @return json data
     * @throws BrokerException BrokerException
     */
    public static byte[] object2JsonBytes(Object object) throws BrokerException {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(object);
        } catch (JsonProcessingException e) {
            log.error("convert object to jsonString failed ", e);
            throw new BrokerException(ErrorCode.JSON_ENCODE_EXCEPTION);
        }
    }

    /**
     * convert object to Bean
     *
     * @param obj object
     * @param typeReference java object type
     * @param <T> template type
     * @return class instance
     */
    public static <T> T object2Bean(Object obj, TypeReference<T> typeReference) {
        return OBJECT_MAPPER.convertValue(obj, typeReference);
    }

    /**
     * convert json byte[] to Object
     *
     * @param json object
     * @param typeReference typeReference
     * @param <T> template type
     * @return class instance
     * @throws BrokerException BrokerException
     */
    public static <T> T json2Object(byte[] json, TypeReference<T> typeReference) throws BrokerException {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("parse extensions failed");
            throw new BrokerException(ErrorCode.JSON_ENCODE_EXCEPTION);
        }
    }

    /**
     * convert json String to Object
     *
     * @param json object
     * @param typeReference typeReference
     * @param <T> template type
     * @return class instance
     * @throws BrokerException BrokerException
     */
    public static <T> T json2Object(String json, TypeReference<T> typeReference) throws BrokerException {
        try {
            return OBJECT_MAPPER.readValue(json, typeReference);
        } catch (IOException e) {
            log.error("parse extensions failed");
            throw new BrokerException(ErrorCode.JSON_ENCODE_EXCEPTION);
        }
    }

    public static boolean isValid(String jsonString) {
        if (StringUtils.isBlank(jsonString)) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(jsonString);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
