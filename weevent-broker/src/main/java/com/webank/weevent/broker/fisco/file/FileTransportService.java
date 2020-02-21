package com.webank.weevent.broker.fisco.file;


import java.util.HashMap;
import java.util.Map;

import com.webank.weevent.BrokerApplication;
import com.webank.weevent.broker.config.FiscoConfig;
import com.webank.weevent.broker.fisco.web3sdk.v2.Web3SDKConnector;
import com.webank.weevent.broker.plugin.IProducer;
import com.webank.weevent.sdk.BrokerException;
import com.webank.weevent.sdk.ErrorCode;
import com.webank.weevent.sdk.FileChunksMeta;
import com.webank.weevent.sdk.JsonHelper;
import com.webank.weevent.sdk.SendResult;
import com.webank.weevent.sdk.WeEvent;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.channel.client.Service;
import org.fisco.bcos.channel.dto.ChannelResponse;

/**
 * File transport service base on AMOP.
 *
 * @author matthewliu
 * @since 2020/02/16
 */
@Slf4j
public class FileTransportService {
    @Data
    static class FileTransportContext {
        private String fileId;
        private FileChunksMeta fileChunksMeta;
        private AMOPChannel channel;
    }

    private final DiskFiles diskFiles;
    private IProducer producer;
    private FiscoConfig fiscoConfig;
    private ZKChunksMeta zkChunksMeta;

    // fileId -> (FileChunksMeta, sender AMOPChannel, receiver AMOPChannel)
    private Map<String, FileTransportContext> fileTransportContexts = new HashMap<>();

    public FileTransportService() {
        this.diskFiles = new DiskFiles(BrokerApplication.weEventConfig.getFilePath());
    }

    public void setProducer(IProducer iProducer) {
        this.producer = iProducer;
    }

    public void setFiscoConfig(FiscoConfig fiscoConfig) {
        this.fiscoConfig = fiscoConfig;
    }

    public void setZkChunksMeta(ZKChunksMeta zkChunksMeta) {
        this.zkChunksMeta = zkChunksMeta;
    }

    private Service initService(String groupId) throws BrokerException {
        return Web3SDKConnector.initService(Long.valueOf(groupId), this.fiscoConfig);
    }

    // CGI interface

    // called by sender cgi
    public void openChannel(FileChunksMeta fileChunksMeta) throws BrokerException {
        if (this.fileTransportContexts.containsKey(fileChunksMeta.getFileId())) {
            log.error("already exist file context, fileId: {}", fileChunksMeta.getFileId());
            return;
        }

        FileTransportContext fileTransportContext = new FileTransportContext();
        fileTransportContext.setFileId(fileChunksMeta.getFileId());
        fileTransportContext.setFileChunksMeta(fileChunksMeta);

        // listen amop sender topic
        String amopTopic = AMOPChannel.genTopic(fileChunksMeta.getTopic(), fileChunksMeta.getFileId());
        log.info("open amop channel for sending file, {}", amopTopic);
        AMOPChannel amopChannel = new AMOPChannel(this, amopTopic, this.initService(fileChunksMeta.getGroupId()), true);
        fileTransportContext.setChannel(amopChannel);

        // send WeEvent to start
        FileEvent fileEvent = new FileEvent(FileEvent.EventType.FileTransportStart);
        fileEvent.setFileChunksMeta(fileChunksMeta);
        Map<String, String> extensions = new HashMap<>();
        extensions.put(WeEvent.WeEvent_FILE, "1");
        extensions.put(WeEvent.WeEvent_FORMAT, "json");
        WeEvent startTransport = new WeEvent(fileChunksMeta.getTopic(), JsonHelper.object2JsonBytes(fileEvent), extensions);

        SendResult sendResult = this.producer.publishSync(startTransport, fileChunksMeta.getGroupId());
        log.info("send start WeEvent to receiver result, {}", sendResult);

        this.fileTransportContexts.put(fileChunksMeta.getFileId(), fileTransportContext);
    }

    public SendResult closeChannel(String fileId) throws BrokerException {
        log.info("close amop sender channel for file, fileId: {}", fileId);

        SendResult sendResult = new SendResult(SendResult.SendResultStatus.ERROR);
        if (!this.fileTransportContexts.containsKey(fileId)) {
            log.error("not exist file context, fileId: {}", fileId);
            return sendResult;
        }

        FileTransportContext fileTransportContext = this.fileTransportContexts.get(fileId);
        this.fileTransportContexts.remove(fileId);
        fileTransportContext.getChannel().close();

        // send WeEvent to close receiver
        FileChunksMeta fileChunksMeta = fileTransportContext.getFileChunksMeta();
        FileEvent fileEvent = new FileEvent(FileEvent.EventType.FileTransportEnd);
        fileEvent.setFileChunksMeta(fileChunksMeta);
        Map<String, String> extensions = new HashMap<>();
        extensions.put(WeEvent.WeEvent_FILE, "1");
        extensions.put(WeEvent.WeEvent_FORMAT, "json");
        byte[] json = JsonHelper.object2JsonBytes(fileEvent);
        WeEvent weEvent = new WeEvent(fileTransportContext.getFileChunksMeta().getTopic(), json, extensions);

        return this.producer.publishSync(weEvent, fileChunksMeta.getGroupId());
    }

    public void sendChunkData(String fileId, int chunkIndex, byte[] data) throws BrokerException {
        if (!this.fileTransportContexts.containsKey(fileId)) {
            log.error("not exist file context, fileId: {}", fileId);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST_CONTEXT);
        }

        log.info("send chunk data via amop channel, {}@{}", fileId, chunkIndex);

        FileTransportContext fileTransportContext = this.fileTransportContexts.get(fileId);
        FileChunksMeta fileChunksMeta = fileTransportContext.getFileChunksMeta();
        if (chunkIndex >= fileChunksMeta.getChunkNum()
                || data.length > fileChunksMeta.getChunkSize()) {
            log.error("invalid chunk data, skip");
            throw new BrokerException(ErrorCode.FILE_INVALID_CHUNK);
        }

        // wait 10s until receiver ready
        int times = 0;
        while (!fileTransportContext.getChannel().checkReceiverAlready() && times < 10) {
            log.info("idle to wait receiver ready");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("idle wait exception", e);
            }

            times++;
        }

        FileEvent fileEvent = new FileEvent(FileEvent.EventType.FileChannelData);
        fileEvent.setFileChunksMeta(fileTransportContext.getFileChunksMeta());
        fileEvent.setChunkIndex(chunkIndex);
        fileEvent.setChunkData(data);
        ChannelResponse rsp = fileTransportContext.getChannel().sendEvent(fileEvent);
        if (rsp.getErrorCode() != 0) {
            BrokerException e = AMOPChannel.toBrokerException(rsp);
            log.error("sender chunk data to remote failed", e);
            throw e;
        } else {
            log.info("sender chunk data to remote success, try to update FileChunksMeta in zookeeper");
            this.flushZKFileChunksMeta(fileEvent.getFileChunksMeta());
        }
    }

    public byte[] downloadChunk(String fileId, int chunkIndex) throws BrokerException {
        log.info("download chunk data, {}@{}", fileId, chunkIndex);
        return this.diskFiles.readChunkData(fileId, chunkIndex);
    }

    // WeEvent interface

    /**
     * call by FileEvent.EventType.FileTransportStart
     * open amop channel to received file data, and create local file(include data and meta).
     *
     * @param fileChunksMeta file meta
     */
    public void prepareReceiveFile(FileChunksMeta fileChunksMeta) {
        String fileId = fileChunksMeta.getFileId();
        if (this.fileTransportContexts.containsKey(fileId)) {
            log.error("already exist file receiving context, fileId: {}", fileId);
        }

        FileTransportContext fileTransportContext = new FileTransportContext();
        fileTransportContext.setFileId(fileId);
        fileTransportContext.setFileChunksMeta(fileChunksMeta);

        log.info("initialize file context for receiving, fileId: {}", fileId);
        String amopTopic = AMOPChannel.genTopic(fileChunksMeta.getTopic(), fileId);
        try {
            // create local file
            this.diskFiles.createFixedLengthFile(fileId, fileChunksMeta.getFileSize());
            this.diskFiles.saveFileMeta(fileChunksMeta);

            log.info("open amop channel for receiving file, {}", fileId);
            AMOPChannel amopChannel = new AMOPChannel(this, amopTopic, this.initService(fileChunksMeta.getGroupId()), false);
            fileTransportContext.setChannel(amopChannel);

            this.fileTransportContexts.put(fileId, fileTransportContext);
        } catch (BrokerException e) {
            log.error("initialize file receiving context failed", e);
        }
    }

    public void cleanUpReceivedFile(String fileId) {
        // close receiver amop channel
        if (this.fileTransportContexts.containsKey(fileId)) {
            log.info("finalize file context for receiving, fileId: {}", fileId);

            FileTransportContext fileTransportContext = this.fileTransportContexts.get(fileId);
            if (fileTransportContext.getChannel() != null) {
                log.error("close amop channel for file, fileId: {}", fileId);
                fileTransportContext.getChannel().close();
            }

            this.fileTransportContexts.remove(fileId);
        }

        // local file CAN NOT delete, because client will downloadChunk after received this WeEvent
    }

    // Notice: always believe FileChunksMeta in local file
    public FileChunksMeta writeChunkData(FileEvent fileEvent) throws BrokerException {
        return this.diskFiles.writeChunkData(fileEvent.getFileChunksMeta().getFileId(), fileEvent.getChunkIndex(), fileEvent.getChunkData());
    }

    public void flushZKFileChunksMeta(FileChunksMeta fileChunksMeta) throws BrokerException {
        try {
            boolean full = this.zkChunksMeta.updateChunks(fileChunksMeta.getFileId(), fileChunksMeta);
            if (full) {
                log.info("all chunk bit is set on, file complete {}", fileChunksMeta.getFileId());
            }
        } catch (BrokerException e) {
            log.error("update FileChunksMeta in zookeeper failed", e);
            throw e;
        }
    }
}