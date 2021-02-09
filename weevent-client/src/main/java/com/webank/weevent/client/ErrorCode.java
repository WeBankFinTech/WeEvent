package com.webank.weevent.client;

/**
 * client error code in (100000, 200000)
 * server error code in (200000, 300000)
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    //client error(100000, 200000)
    TOPIC_ALREADY_EXIST(100100, "topic already exist"),
    TOPIC_NOT_EXIST(100101, "topic not exist"),
    TOPIC_EXCEED_MAX_LENGTH(100102, "topic name exceeds max length[64 bytes]"),
    TOPIC_IS_BLANK(100103, "topic name is blank"),
    TOPIC_PAGE_INDEX_INVALID(100104, "page index should be an integer start from 0"),
    TOPIC_LIST_IS_NULL(100105, "topic list is empty or has empty value"),
    TOPIC_CONTAIN_INVALID_CHAR(100106, "topic name contain invalid char, ascii must be in[32, 128] except wildcard(+,#)"),
    TOPIC_PAGE_SIZE_INVALID(100107, "page size should be an integer in(1, 100)"),
    TOPIC_NOT_MATCH(100108, "topic name not match with last subscribe"),
    PATTERN_INVALID(100109, "invalid topic pattern"),
    TOPIC_TAG_NOT_MATCH(100110, "topic tag not match with last subscribe"),
    TOPIC_CREATE_FAILED(100111, "create topic failed."),

    EVENT_CONTENT_IS_BLANK(100200, "event content is blank"),
    EVENT_CONTENT_EXCEEDS_MAX_LENGTH(100201, "event content exceeds max length[10k bytes]"),
    SEND_CALL_BACK_IS_NULL(100202, "send call back instance is null"),
    EVENT_CONTENT_CHARSET(100203, "event content must be utf-8"),
    EVENT_EXTENSIONS_EXCEEDS_MAX_LENGTH(100204, "event extensions exceeds max length[1k bytes]"),
    EVENT_EXTENSIONS_IS_NUll(100205, "event extensions is null"),
    EVENT_EXTENSIONS_KEY_INVALID(100206, "event extensions key not startwith weevent-"),

    EVENT_ID_IS_BLANK(100300, "eventId is blank"),
    EVENT_ID_EXCEEDS_MAX_LENGTH(100301, "eventId exceeds max length[64 bytes]"),
    EVENT_ID_IS_ILLEGAL(100302, "eventId is illegal"),
    EVENT_ID_NOT_EXIST(100303, "eventId is not exist"),
    EVENT_ID_IS_MISMATCH(100304, "eventId is mismatch with block chain"),
    EVENT_GROUP_ID_NOT_FOUND(100305, "event group id is not found"),
    EVENT_GROUP_ID_INVALID(100306, "event group id should be a string start from 1"),
    INVALID_BLOCK_HEIGHT(100307, "invalid block height"),

    OFFSET_IS_BLANK(100500, "subscribe interface offset param is blank"),
    URL_INVALID_FORMAT(100501, "invalid url format"),
    URL_CONNECT_FAILED(100502, "failed to connect url"),
    SUBSCRIPTIONID_IS_BLANK(100503, "subscribe interface subscription id is blank"),
    SUBSCRIPTIONID_NOT_EXIST(100504, "subscribe id is not exist"),
    SUBSCRIPTIONID_FORMAT_INVALID(100505, "subscribe id format invalid"),
    TOPIC_TAG_IS_BLANK(100506, "topic's tag is blank"),
    SUBSCRIPTIONID_ALREADY_EXIST(100507, "subscriptionId is already exist, can not subscribe again"),

    SDK_TLS_INIT_FAILED(101001, "init tsl ca failed"),
    SDK_JMS_INIT_FAILED(101002, "init jms connection factory failed"),
    SDK_EXCEPTION_STOMP_EXECUTE(101003, "stomp command execute failed"),
    SDK_EXCEPTION_STOMP_TIMEOUT(101004, "stomp command invoke timeout"),
    SDK_STOMP_CONNECTION_BREAKDOWN(101005, "stomp connection is breakdown"),
    SDK_JMS_EXCEPTION(101010, "jms exception"),
    USER_PASSWORD_ISBLANK(101011, "stomp password is blank"),
    USER_NAME_ISBLANK(101012, "stomp user name is blank"),
    PARAM_ISBLANK(101013, "the input param is blank"),
    PARAM_ISEMPTY(101014, "the input param is empty"),
    PARAM_ISNULL(101014, "the input param is null"),
    PARAM_IS_NOT_MAP(101015, "the input param class is not Map"),
    NO_PERMISSION(101016, "no permission to publish event"),
    OPERATOR_ALREADY_EXIST(101017, "operator already exist"),
    OPERATOR_NOT_EXIST(101018, "operator not exist"),
    OPERATOR_ADDRESS_IS_NULL(101020, "the operatorAddress is null"),
    OPERATOR_ADDRESS_ILLEGAL(101021, "the operatorAddress is illegal"),
    TRANSACTIONHEX_IS_NULL(101022, "the transactionHex is null"),
    TRANSACTIONHEX_ILLEGAL(101023, "the transactionHex is illegal"),

    FILE_NAME_IS_NULL(102000, "the upload file name is null"),
    FILE_ID_IS_NULL(102001, "the upload fileId is null"),
    FILE_ID_ILLEGAL(102002, "the upload fileId is illegal"),
    FILE_SIZE_ILLEGAL(102003, "the upload file size is illegal"),
    FILE_MD5_IS_NULL(102004, "the upload file md5 is null"),
    CHUNK_IDX_ILLEGAL(102005, "the upload file chunk index is illegal"),
    CHUNK_DATA_ILLEGAL(102006, "the upload file chunk data is illegal"),
    FILE_CHUNK_INDEX_ILLEGAL(102007, "the upload file chunk idx is illegal"),
    FILE_CHUNK_DATA_IS_NULL(102008, "the upload file chunk data is null"),
    FILE_UPLOAD_FAILED(102009, "file upload failed"),
    FILE_GENERATE_MD5_ERROR(102010, "file generate md5 failed"),
    FILE_DOWNLOAD_ERROR(102011, "file download failed"),
    FILE_MD5_MISMATCH(102012, "file md5 mismatch"),
    LOCAL_FILE_IS_EMPTY(102013, "local file path is empty"),
    LOCAL_FILE_NOT_EXIST(102014, "local file not exist"),
    ENCODE_FILE_NAME_ERROR(102015, "encode file name error"),
    DECODE_FILE_NAME_ERROR(102016, "decode file name error"),
    ENCODE_TOPIC_ERROR(102017, "encode topic error"),
    ENCODE_EVENT_ID_ERROR(102018, "encode event id error"),

    HTTP_REQUEST_EXECUTE_ERROR(102100, "http request execute failed"),
    BUILD_HTTP_URL_ERROR(102101, "build http url failed"),
    HTTP_RESPONSE_FAILED(102102, "http response failed"),
    HTTP_RESPONSE_ENTITY_EMPTY(102103, "http response entity is empty"),
    HTTPENTITY_TO_BYTEARRAY_ERROR(102104, "convert httpEntity to byte[] error"),

    //server error(200000, 300000)
    UNKNOWN_ERROR(200000, "unknown error, see more details in log"),
    CGI_INVALID_INPUT(200001, "invalid input param"),
    TOPIC_CONTROLLER_IS_NULL(200100, "get topic control address from CNS failed, please deploy it first"),
    CONSUMER_ALREADY_STARTED(200102, "consumer already started"),
    PRODUCER_SEND_CALLBACK_IS_NULL(200103, "producer send callback is null"),
    CONSUMER_LISTENER_IS_NULL(200104, "consumer listener is null"),

    BCOS_SDK_BUILD_ERROR(200200, "build BcosSDK failed"),
    TRANSACTION_TIMEOUT(200201, "the transaction is timeout."),
    TRANSACTION_EXECUTE_ERROR(200202, "the transaction does not correctly executed."),
    EVENT_COMPRESS_ERROR(200204, "event compress error"),
    GET_BLOCK_HEIGHT_ERROR(200205, "get block height failed due to InterruptedException|ExecutionException|TimeoutException|RuntimeException"),
    TRANSACTION_RECEIPT_IS_NULL(200206, "transaction reception is null"),
    DEPLOY_CONTRACT_ERROR(200207, "deploy contract failed"),
    LOAD_CONTRACT_ERROR(200208, "load contract failed"),
    WEB3SDK_INIT_ERROR(200209, "init web3sdk failed"),
    WEB3SDK_VERSION_NOT_SUPPORT(200210, "FISCO-BCOS 1.x, do not support group"),
    WEB3SDK_UNKNOWN_GROUP(200211, "FISCO-BCOS 2.x, unknown group id"),
    SUBSCRIPTION_NOTIFY_QUEUE_FULL(200212, "onEvent is blocked too long, notify queue is full"),
    UNKNOWN_SOLIDITY_VERSION(200213, "unknown contract version"),
    WEB3SDK_RPC_ERROR(200214, "we3sdk's rpc failed"),
    JSON_ENCODE_EXCEPTION(200215, "encode Object to json failed"),
    JSON_DECODE_EXCEPTION(200216, "decode Object from json failed"),

    FABRICSDK_CHANNEL_NAME_INVALID(200300, "Fabric 1.4x, channel name invalid"),
    FABRICSDK_GETBLOCKINFO_ERROR(200301, "Fabric 1.4x, get blockInfo error"),
    FABRICSDK_CHAINCODE_INVOKE_FAILED(200302, "Fabric 1.4x, execute chaincode invoke failed"),

    ZOOKEEPER_ERROR(200400, "access zookeeper failed"),
    ZOOKEEPER_INVALID_PATH(200401, "invalid zookeeper path"),
    ZOOKEEPER_UNKNOWN_KEY(200402, "unknown key in zookeeper"),
    ZOOKEEPER_EXIST_KEY(200403, "key already exist in zookeeper"),

    FILE_NOT_EXIST_PATH(200500, "not exist file path"),
    FILE_INVALID_FILE_CHUNK_SIZE(200501, "file chunk size must be in (0, 2M)"),
    FILE_NOT_EXIST_CONTEXT(200502, "not exist file context"),
    FILE_INVALID_CHUNK(200503, "invalid chunk data"),
    FILE_NOT_EXIST(200504, "not exist file"),
    FILE_READ_EXCEPTION(200505, "read file exception"),
    FILE_WRITE_EXCEPTION(200506, "write file exception"),
    FILE_NOT_ENOUGH_SPACE(200507, "not enough disk space"),
    FILE_RECEIVE_CONTEXT_NOT_READY(200508, "receive file context is not ready"),
    FILE_SENDER_RECEIVER_CONFLICT(200509, "can't publish and subscribe a file in the same node"),
    FILE_EXIST_CONTEXT(200510, "exist file context"),
    FILE_INIT_VERIFY_FAILED(200511, "initialize PEM for verify topic failed"),
    FILE_EXIST_AND_NOT_ALLOW_OVERWRITE(200512, "The file already exists in the directory, and do not allow overwrite"),
    FILE_GEN_PEM_BC_FAILED(200513,"generate pem file failed due to bouncy castle exception."),
    FILE_PEM_KEY_INVALID(200514, "public or private key pem file invalid."),
    FILE_GEN_LOCAL_FILE_NAME_FAILED(200515, "fileChunksMeta corresponding to fieldId not exist"),
    FILE_GET_TOPIC_FROM_OLD2NEW_TOPIC_ERROR(200516, "get topic from old2NewTopic error"),

    MQTT_UNKNOWN_COMMAND(200600, "unknown mqtt command"),
    MQTT_ENCODE_FAILED(200601, "encode mqtt message failed"),
    MQTT_DECODE_FAILED(200602, "decode mqtt message failed"),
    MQTT_CONNECT_CONFLICT(200610, "connect command conflict with specification 3.1.1"),
    MQTT_UNKNOWN_CLIENT_ID(200611, "unknown client id"),
    MQTT_NOT_SUPPORT_QOS2(200612, "do not support qos=2"),
    MQTT_NOT_PERMISSION(200613, "not permission"),

    FTP_INVALID_USERNAME_PASSWD(200700, "invalid username or password"),
    FTP_INVALID_HOST_PORT(200701, "invalid host or port"),
    FTP_LOGIN_FAILED(200702, "ftp login failed"),
    FTP_CLIENT_NOT_CONNECT_TO_SERVER(200703, "ftp client not connected to a server"),
    FTP_NOT_EXIST_PATH(200704, "ftp not exist file path"),
    FTP_UPLOAD_FILE_FAILED(200705, "ftp upload file failed"),
    FTP_CHANGE_WORKING_DIR_FAILED(200706, "ftp change working directory failed"),
    FTP_MAKE_DIR_FAILED(200707, "make directory failed"),
    FTP_UNKNOWN_ERROR(200708, "unknown ftp error"),
    FTP_INVALID_REMOTE_PATH(200709, "remote path invalid"),
    FTP_RETRIEVE_FILE_FAILED(200710, "remote path invalid"),
    FTP_UNKNOWN_REMOTE_FILE(200711, "unknown remote file"),
    FTP_NOT_FILE(200712, "it's not a file"),
    FTP_LIST_FILE_FAILED(200713, "list file error"),

    SEND_AMOP_MESSAGE_FAILED(200800, "send amop msg failed."),
    UNKNOWN_AMOP_SUB_TOPIC(200801, "unknown amop subscribe topic")
    ;

    /**
     * error code
     */
    private int code;

    /**
     * error message
     */
    private String codeDesc;

    /**
     * Error Code Constructor.
     *
     * @param code The ErrorCode
     * @param codeDesc The ErrorCode Description
     */
    ErrorCode(int code, String codeDesc) {
        this.code = code;
        this.codeDesc = codeDesc;
    }

    /**
     * Get the Error Code.
     *
     * @return the ErrorCode
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the ErrorCode Description.
     *
     * @return the ErrorCode Description
     */
    public String getCodeDesc() {
        return codeDesc;
    }

    /**
     * @param code error code
     * @return error desc
     */
    public static String getDescByCode(int code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (code == errorCode.getCode()) {
                return errorCode.getCodeDesc();
            }
        }
        return "";
    }
}
