package com.webank.weevent.file.inner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.JsonHelper;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.client.WeEventPlus;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.dto.AmopMsgResponse;
import com.webank.weevent.file.dto.FileChunksMetaPlus;
import com.webank.weevent.file.dto.FileChunksMetaStatus;
import com.webank.weevent.file.dto.FileEvent;
import com.webank.weevent.file.dto.FileTransportStats;
import com.webank.weevent.file.service.FileChunksMeta;

import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.amop.AmopResponse;
/**
 * File transport service base on AMOP.
 *
 * @author matthewliu
 * @since 2020/02/16
 */
@Slf4j
public class FileTransportService {
    private final FiscoConfig fiscoConfig;
    private final IProducer producer;
    private final int timeout;
    private final String host;
    private final int fileChunkSize;
    private final DiskFiles diskFiles;

    // following ONLY used in sender side
    private AMOPChannel channel;
    // fileId <-> FileChunksMeta
    private Map<String, FileChunksMeta> fileTransportContexts = new ConcurrentHashMap<>();

    // fileTransportService for common transport
    public FileTransportService(
            FiscoConfig fiscoConfig,
            IProducer iProducer,
            String host,
            String filePath,
            int fileChunkSize,
            String groupId) throws BrokerException {
        this.fiscoConfig = fiscoConfig;
        this.producer = iProducer;
        this.timeout = fiscoConfig.getWeEventCoreConfig().getTimeout();

        log.info("host: {}, file path: {}, chunk size: {}", host, filePath, fileChunkSize);
        if (fileChunkSize <= 0 || fileChunkSize > 2 * 1024 * 1024) {
            log.error("invalid file chunk size");
            throw new BrokerException(ErrorCode.FILE_INVALID_FILE_CHUNK_SIZE);
        }
        this.host = host;
        this.fileChunkSize = fileChunkSize;
        this.diskFiles = new DiskFiles(filePath);

        // init common amop channel
        log.info("init AMOP channel for common transport, groupId: {}", groupId);
        this.channel = new AMOPChannel(this);
    }

    public AMOPChannel getChannel() {
        return this.channel;
    }

    public FiscoConfig getFiscoConfig() {
        return fiscoConfig;
    }

    public DiskFiles getDiskFiles() {
        return this.diskFiles;
    }

    public IProducer getProducer() {
        return this.producer;
    }

    public FileChunksMetaPlus verify(String eventId, String groupId) throws BrokerException {
        WeEvent event = this.producer.getEvent(eventId, groupId);
        if (this.isFileEvent(event)) {
            FileEvent fileEvent = JsonHelper.json2Object(event.getContent(), FileEvent.class);

            FileChunksMetaPlus fileChunksMetaPlus = new FileChunksMetaPlus();
            fileChunksMetaPlus.setFile(fileEvent.getFileChunksMeta());
            if (event.getExtensions().containsKey(WeEvent.WeEvent_PLUS)) {
                WeEventPlus weEventPlus = JsonHelper.json2Object(event.getExtensions().get(WeEvent.WeEvent_PLUS), WeEventPlus.class);
                fileChunksMetaPlus.setPlus(weEventPlus);
            }
            return fileChunksMetaPlus;
        }

        log.error("it is not a file event");
        return null;
    }

    private boolean isFileEvent(WeEvent event) {
        return event.getExtensions().containsKey(WeEvent.WeEvent_FILE)
                && event.getExtensions().containsKey(WeEvent.WeEvent_FORMAT)
                && "json".equals(event.getExtensions().get(WeEvent.WeEvent_FORMAT));
    }

    public FileTransportStats stats(boolean all, String groupId, String topicName, String nodeAddress) {
        FileTransportStats fileTransportStats = new FileTransportStats();

        // sender
        Map<String, List<FileChunksMetaStatus>> senders = new HashMap<>();
        for (String topic : channel.getSenderTopics()) {
            List<FileChunksMetaStatus> filePlus = fileTransportContexts.values().stream()
                    .filter(item -> item.getTopic().equals(topic))
                    .map(FileChunksMetaStatus::new)
                    .collect(Collectors.toList());
            senders.put(topic, filePlus);
        }
        fileTransportStats.getSender().put(groupId, senders);

        // receiver
        List<FileChunksMeta> localFiles = this.diskFiles.listNotCompleteFiles(all, groupId, topicName, nodeAddress);
        Map<String, List<FileChunksMetaStatus>> receivers = new HashMap<>();
        for (String topic : channel.getSubTopics()) {
            List<FileChunksMetaStatus> filePlus = localFiles.stream()
                    .filter(item -> topic.equals(item.getTopic()))
                    .map(FileChunksMetaStatus::new)
                    .collect(Collectors.toList());
            receivers.put(topic, filePlus);
        }
        fileTransportStats.getReceiver().put(groupId, receivers);


        return fileTransportStats;
    }

    public boolean getFileExistence(String fileName, String topic, String groupId, String nodeAddress, String role) throws BrokerException {

        FileChunksMeta fileChunksMeta = new FileChunksMeta("", fileName, 0, "", topic, groupId, false, nodeAddress, role);

        return channel.isFileExist(fileChunksMeta);
    }

    public FileChunksMeta openChannel(FileChunksMeta fileChunksMeta) throws BrokerException {
        if (!this.producer.exist(fileChunksMeta.getTopic(), fileChunksMeta.getGroupId())) {
            log.error("topic:{} not exist, fileId: {}", fileChunksMeta.getTopic(), fileChunksMeta.getFileId());
            throw new BrokerException(ErrorCode.TOPIC_NOT_EXIST);
        }
        if (this.fileTransportContexts.containsKey(fileChunksMeta.getFileId())) {
            log.error("already exist file context, fileId: {}", fileChunksMeta.getFileId());
            throw new BrokerException(ErrorCode.FILE_EXIST_CONTEXT);
        }

        // sender side DO NOT need chunk size, skip to minimize memory use
        // fileChunksMeta.setChunkSize(this.fileChunkSize);
        // sender side can fail over automatic
        // fileChunksMeta.setHost(this.host);

        // create chunk meta in receiver
        //AMOPChannel channel = this.getChannel(fileChunksMeta.getGroupId());
        FileChunksMeta remoteFileChunksMeta = channel.createReceiverFileContext(fileChunksMeta);
        // it's not used in sender side
        remoteFileChunksMeta.setHost("");

        // cache chunk meta
        this.fileTransportContexts.put(fileChunksMeta.getFileId(), remoteFileChunksMeta);

        return remoteFileChunksMeta;
    }

    // sender side can fail over automatic
    private FileChunksMeta getSenderFileChunksMeta(String topic, String groupId, String fileId) throws BrokerException {
        if (this.fileTransportContexts.containsKey(fileId)) {
            log.info("get file context in memory");
            return this.fileTransportContexts.get(fileId);
        } else {
            log.info("get file context from remote");
            FileChunksMeta fileChunksMeta = this.getReceiverFileChunksMeta(topic, groupId, fileId);
            this.fileTransportContexts.put(fileId, fileChunksMeta);
            return fileChunksMeta;
        }
    }

    // get remote chunk meta from receiver
    public FileChunksMeta getReceiverFileChunksMeta(String topic, String groupId, String fileId) throws BrokerException {
        // get remote chunk meta from receiver
        FileChunksMeta remoteFileChunksMeta = channel.getReceiverFileContext(topic, fileId);
        if (remoteFileChunksMeta == null) {
            log.error("not exist receive file context");
            throw new BrokerException(ErrorCode.FILE_RECEIVE_CONTEXT_NOT_READY);
        }

        // it's not used in sender side
        remoteFileChunksMeta.setHost("");
        return remoteFileChunksMeta;
    }

    public FileChunksMeta closeChannel(String topic, String fileId) throws BrokerException {
        log.info("close AMOP sender channel for file, fileId: {}", fileId);
        this.fileTransportContexts.remove(fileId);

        // clean up receiver context
        return channel.cleanUpReceiverFileContext(topic, fileId);
    }

    public SendResult sendSign(FileChunksMeta fileChunksMeta) throws BrokerException {
        // send sign to WeEvent
        FileEvent fileEvent = new FileEvent(FileEvent.EventType.FileTransport, fileChunksMeta.getFileId());
        // not need detail
        fileChunksMeta.clearPrivacy();
        fileEvent.setFileChunksMeta(fileChunksMeta);
        Map<String, String> extensions = new HashMap<>();
        extensions.put(WeEvent.WeEvent_FILE, "1");
        extensions.put(WeEvent.WeEvent_FORMAT, "json");
        byte[] json = JsonHelper.object2JsonBytes(fileEvent);
        WeEvent weEvent = new WeEvent(fileChunksMeta.getTopic(), json, extensions);
        return this.producer.publish(weEvent, fileChunksMeta.getGroupId(), this.timeout);
    }

    public void sendChunkData(String topic, String groupId, String fileId, int chunkIndex, byte[] data) throws BrokerException {
        // check file context exist
        FileChunksMeta fileChunksMeta = this.getSenderFileChunksMeta(topic, groupId, fileId);

        log.info("send chunk data via AMOP channel, {}#{}", fileId, chunkIndex);

        FileEvent fileEvent = new FileEvent(FileEvent.EventType.FileChannelData, fileId);
        fileEvent.setChunkIndex(chunkIndex);
        fileEvent.setChunkData(data);

        try {
            AmopResponse rsp = channel.sendEvent(topic, fileEvent);
            if (rsp.getErrorCode() != ErrorCode.SUCCESS.getCode()) {
                log.error("receive sender chunk data to remote failed, rsp:{}", rsp.getErrorMessage());
                throw AMOPChannel.toBrokerException(rsp);
            }

            AmopMsgResponse amopMsgResponse = JsonHelper.json2Object(rsp.getAmopMsgIn().getContent(), AmopMsgResponse.class);
            if (amopMsgResponse.getErrorCode() != ErrorCode.SUCCESS.getCode()) {
                log.error("sender chunk data to remote failed, rsp:{}", amopMsgResponse.getErrorMessage());
                throw AMOPChannel.toBrokerException(amopMsgResponse);
            }

            log.info("sender chunk data to remote success");
            // local cached chunkStatus is not consistency, but show in stats and log only
            fileChunksMeta.getChunkStatus().set(chunkIndex);
        } catch (TimeoutException e) {
            log.error("TimeoutException while send amop request");
            throw new BrokerException(ErrorCode.SEND_AMOP_MESSAGE_FAILED);
        } catch (InterruptedException e) {
            log.error("InterruptedException while send amop request");
            Thread.currentThread().interrupt();
            throw new BrokerException(ErrorCode.SEND_AMOP_MESSAGE_FAILED);
        }
    }

    // Notice: receiver always believe FileChunksMeta in local file

    public byte[] downloadChunk(String fileId, int chunkIndex) throws BrokerException {
        log.info("download chunk data, {}#{}", fileId, chunkIndex);
        return this.diskFiles.readChunkData(fileId, chunkIndex);
    }

    public FileChunksMeta prepareReceiveFile(FileChunksMeta fileChunksMeta) throws BrokerException {
        log.info("initialize file context for receiving, fileName: {}", fileChunksMeta.getFileName());

        // create local file
        this.diskFiles.createFixedLengthFile(fileChunksMeta);
        // initialize chunk size
        fileChunksMeta.initChunkSize(this.fileChunkSize);
        // set local host
        fileChunksMeta.setHost(this.host);
        this.diskFiles.saveFileMeta(fileChunksMeta);
        return fileChunksMeta;
    }

    public FileChunksMeta loadFileChunksMeta(String fileId) throws BrokerException {
        return this.diskFiles.loadFileMeta(fileId);
    }

    public FileChunksMeta cleanUpReceivedFile(String fileId) throws BrokerException {
        log.info("finalize file context for receiving, fileId: {}", fileId);

        // local file CAN NOT delete, because client will downloadChunk after received this WeEvent
        FileChunksMeta fileChunksMeta = this.diskFiles.loadFileMeta(fileId);
        if (!fileChunksMeta.checkChunkFull()) {
            log.info("try to delete file not complete, {}", fileId);
            // delete not complete file in local disk
            this.diskFiles.cleanUp(fileId);
        }

        return fileChunksMeta;
    }

    public void writeChunkData(FileEvent fileEvent) throws BrokerException {
        this.diskFiles.writeChunkData(fileEvent.getFileId(), fileEvent.getChunkIndex(), fileEvent.getChunkData());
    }

    public boolean checkFileExist(FileChunksMeta fileChunksMeta) throws BrokerException {
        log.info("check if the file exists, fileName: {}", fileChunksMeta.getFileName());

        // check if the file exists
        return this.diskFiles.checkFileExist(fileChunksMeta);
    }
}
