package com.webank.weevent.core.fisco.util;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import com.webank.weevent.core.fisco.constant.WeEventConstants;
import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.WeEvent;

import org.apache.commons.lang3.StringUtils;

/**
 * @author websterchen
 * @version 1.0
 * @since 2019/1/28
 */
public class ParamCheckUtils {
    public static void validateTopicName(String topic) throws BrokerException {
        if (StringUtils.isBlank(topic)) {
            throw new BrokerException(ErrorCode.TOPIC_IS_BLANK);
        }
        if (topic.length() > WeEventConstants.TOPIC_NAME_MAX_LENGTH) {
            throw new BrokerException(ErrorCode.TOPIC_EXCEED_MAX_LENGTH);
        }
        if (topic.endsWith(WeEvent.LAYER_SEPARATE)) {
            throw new BrokerException(ErrorCode.TOPIC_CONTAIN_INVALID_CHAR);
        }
        for (char x : topic.toCharArray()) {
            if (x < 32 || x > 128
                    || x == WeEvent.WILD_CARD_ONE_LAYER.charAt(0)
                    || x == WeEvent.WILD_CARD_ALL_LAYER.charAt(0)) {
                throw new BrokerException(ErrorCode.TOPIC_CONTAIN_INVALID_CHAR);
            }
        }
    }

    public static void validateOffset(String offset) throws BrokerException {
        if (StringUtils.isBlank(offset)) {
            throw new BrokerException(ErrorCode.OFFSET_IS_BLANK);
        }
    }

    public static void validateSubscriptionId(String subscriptionId) throws BrokerException {
        if (StringUtils.isBlank(subscriptionId)) {
            throw new BrokerException(ErrorCode.SUBSCRIPTIONID_IS_BLANK);
        }

        try {
            UUID.fromString(subscriptionId);
        } catch (IllegalArgumentException e) {
            throw new BrokerException(ErrorCode.SUBSCRIPTIONID_FORMAT_INVALID);
        }
    }

    public static void validateEventId(String topicName, String eventId, Long blockHeight) throws BrokerException {
        if (StringUtils.isBlank(eventId)) {
            throw new BrokerException(ErrorCode.EVENT_ID_IS_ILLEGAL);
        }
        if (eventId.length() > WeEventConstants.EVENT_ID_MAX_LENGTH) {
            throw new BrokerException(ErrorCode.EVENT_ID_EXCEEDS_MAX_LENGTH);
        }

        if (!StringUtils.isBlank(topicName)
                && !DataTypeUtils.genTopicNameHash(topicName).equals(DataTypeUtils.decodeTopicNameHash(eventId))) {
            throw new BrokerException(ErrorCode.EVENT_ID_IS_ILLEGAL);
        }

        Long lastEventSeq = DataTypeUtils.decodeSeq(eventId);
        Long lastBlock = DataTypeUtils.decodeBlockNumber(eventId);
        if (lastEventSeq <= 0 || lastBlock <= 0) {
            throw new BrokerException(ErrorCode.EVENT_ID_IS_ILLEGAL);
        }
        if (blockHeight == 0L) {
            throw new BrokerException(ErrorCode.GET_BLOCK_HEIGHT_ERROR);
        } else if (lastBlock > blockHeight) {
            throw new BrokerException(ErrorCode.EVENT_ID_IS_MISMATCH);
        }
    }


    public static void validateEvent(WeEvent event) throws BrokerException {
        if (event.getContent() == null) {
            throw new BrokerException(ErrorCode.EVENT_CONTENT_IS_BLANK);
        }
        validateTopicName(event.getTopic());
        validateEventContent(new String(event.getContent(), StandardCharsets.UTF_8));

        if (event.getExtensions() != null) {
            checkExtensionsKeyIsLegal(event.getExtensions());
            if (event.getExtensions().toString().length() > WeEventConstants.EVENT_EXTENSIONS_MAX_LENGTH) {
                throw new BrokerException(ErrorCode.EVENT_EXTENSIONS_EXCEEDS_MAX_LENGTH);
            }
        } else {
            throw new BrokerException(ErrorCode.EVENT_EXTENSIONS_IS_NUll);
        }
    }

    public static void validateGroupId(String groupId, List<String> groups) throws BrokerException {
        if (!groups.contains(groupId)) {
            throw new BrokerException(ErrorCode.WEB3SDK_UNKNOWN_GROUP);
        }
    }

    public static void validateChannelName(String channelName, List<String> channelNames) throws BrokerException {
        if (!channelNames.contains(channelName)) {
            throw new BrokerException(ErrorCode.FABRICSDK_CHANNEL_NAME_INVALID);
        }
    }

    public static void validateEventContent(String eventContent) throws BrokerException {
        if (StringUtils.isBlank(eventContent)) {
            throw new BrokerException(ErrorCode.EVENT_CONTENT_IS_BLANK);
        }

        if (eventContent.length() > WeEventConstants.EVENT_CONTENT_MAX_LENGTH) {
            throw new BrokerException(ErrorCode.EVENT_CONTENT_EXCEEDS_MAX_LENGTH);
        }
    }

    // check format then try to connect
    public static void validateUrl(String url) throws BrokerException {
        try {
            URL conn = new URL(url);
            conn.openConnection().connect();
        } catch (MalformedURLException e) {
            throw new BrokerException(ErrorCode.URL_INVALID_FORMAT);
        } catch (IOException e) {
            throw new BrokerException(ErrorCode.URL_CONNECT_FAILED);
        }
    }

    public static void checkExtensionsKeyIsLegal(Map<String, String> extensions) throws BrokerException {
        for (Map.Entry<String, String> extension : extensions.entrySet()) {
            if (!extension.getKey().startsWith(WeEventConstants.EXTENSIONS_PREFIX_CHAR)) {
                throw new BrokerException(ErrorCode.EVENT_EXTENSIONS_KEY_INVALID);
            }
        }
    }

    public static void validatePagIndexAndSize(Integer pageIndex, Integer pageSize) throws BrokerException {
        if (pageIndex == null || pageIndex < 0) {
            throw new BrokerException(ErrorCode.TOPIC_PAGE_INDEX_INVALID);
        }
        if (pageSize == null || pageSize <= 0 || pageSize > 100) {
            throw new BrokerException(ErrorCode.TOPIC_PAGE_SIZE_INVALID);
        }
    }

    public static void validateAddress(String address) throws BrokerException {
        if (StringUtils.isBlank(address)) {
            throw new BrokerException(ErrorCode.OPERATOR_ADDRESS_IS_NULL);
        }
        if (!Pattern.compile(WeEventConstants.FISCO_BCOS_ADDRESS_PATTERN).matcher(address).matches()) {
            throw new BrokerException(ErrorCode.OPERATOR_ADDRESS_ILLEGAL);
        }
    }

    public static void validateTransactionHex(String transactionHex) throws BrokerException {
        if (StringUtils.isBlank(transactionHex)) {
            throw new BrokerException(ErrorCode.TRANSACTIONHEX_IS_NULL);
        }
        if (!Pattern.compile(WeEventConstants.SIGN_DATA_PATTERN).matcher(transactionHex).matches()) {
            throw new BrokerException(ErrorCode.TRANSACTIONHEX_ILLEGAL);
        }
    }

    public static void validateFileName(String fileName) throws BrokerException {
        if (StringUtils.isBlank(fileName)) {
            throw new BrokerException(ErrorCode.FILE_NAME_IS_NULL);
        }
    }

    public static void validateFileId(String fileId) throws BrokerException {
        if (StringUtils.isBlank(fileId)) {
            throw new BrokerException(ErrorCode.FILE_ID_IS_NULL);
        }
        if (fileId.length() != 32) {
            throw new BrokerException(ErrorCode.FILE_ID_ILLEGAL);
        }

    }

    public static void validateFileSize(long fileSize) throws BrokerException {
        if (fileSize <= 0) {
            throw new BrokerException(ErrorCode.FILE_SIZE_ILLEGAL);
        }
    }

    public static void validateFileMd5(String md5) throws BrokerException {
        if (StringUtils.isBlank(md5)) {
            throw new BrokerException(ErrorCode.FILE_MD5_IS_NULL);
        }
    }

    public static void validateChunkIdx(int chunkIdx) throws BrokerException {
        if (chunkIdx < 0) {
            throw new BrokerException(ErrorCode.FILE_CHUNK_INDEX_ILLEGAL);
        }
    }

    public static void validateChunkData(byte[] chunkData) throws BrokerException {
        if (chunkData == null || chunkData.length == 0) {
            throw new BrokerException(ErrorCode.FILE_CHUNK_DATA_IS_NULL);
        }
    }

}
