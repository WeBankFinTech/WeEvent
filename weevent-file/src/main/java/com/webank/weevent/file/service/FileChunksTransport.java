package com.webank.weevent.file.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.core.fisco.util.ParamCheckUtils;
import com.webank.weevent.core.fisco.util.WeEventUtils;
import com.webank.weevent.file.inner.FileTransportService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.DigestUtils;

/**
 * Upload and Download File in chunks.
 *
 * @author matthewliu
 * @since 2020/02/12
 */
@Slf4j
public class FileChunksTransport {
    private FileTransportService fileTransportService;

    // retry upload/download chunk times
    private static final int CHUNK_RETRY_COUNT = 5;

    public FileChunksTransport(FileTransportService fileTransportService) {
        this.fileTransportService = fileTransportService;
    }

    public FileChunksMeta upload(String localFile, String topic, String groupId, boolean overwrite) throws BrokerException, IOException {
        log.info("try to upload file {}", localFile);

        File file = new File(localFile);
        String md5;
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            md5 = DigestUtils.md5DigestAsHex(fileInputStream);
        } catch (Exception e) {
            log.error("md5sum failed", e);
            throw new BrokerException(ErrorCode.FILE_GENERATE_MD5_ERROR);
        }

        // get file initial information
        FileChunksMeta fileChunksMeta;
        try (RandomAccessFile f = new RandomAccessFile(file, "r")) {
            fileChunksMeta = new FileChunksMeta("",
                    file.getName(),
                    file.length(),
                    md5,
                    topic,
                    groupId,
                    overwrite);
            // get chunk information
            fileChunksMeta = this.openFileChunksInfo(fileChunksMeta);

            // upload every single chunk data
            for (int chunkIdx = 0; chunkIdx < fileChunksMeta.getChunkNum(); chunkIdx++) {
                this.uploadChunkDetails(f, fileChunksMeta, chunkIdx);
            }

            boolean chunkFullUpload = checkChunkFullUpload(f, fileChunksMeta);
            if (!chunkFullUpload) {
                log.error("upload file :{} failed", file.getName());
                throw new BrokerException(ErrorCode.FILE_UPLOAD_FAILED);
            }
        }

        log.info("upload file complete, {}", localFile);
        return this.closeChunk(fileChunksMeta);
    }

    private void uploadChunkDetails(RandomAccessFile f, FileChunksMeta fileChunksMeta, int chunkIdx) throws IOException, BrokerException {
        int size = fileChunksMeta.getChunkSize();
        if (chunkIdx == fileChunksMeta.getChunkNum() - 1) {
            size = (int) (fileChunksMeta.getFileSize() % fileChunksMeta.getChunkSize());
        }
        f.seek((long) chunkIdx * fileChunksMeta.getChunkSize());
        byte[] chunkData = new byte[size];
        int readSize = f.read(chunkData);
        if (readSize != size) {
            log.error("read file exception, chunkIdx: {}", chunkIdx);
            throw new BrokerException(ErrorCode.FILE_READ_EXCEPTION);
        }

        if (this.uploadChunk(fileChunksMeta, chunkIdx, chunkData)) {
            log.info("upload file chunk data success, {}@{}", fileChunksMeta.getFileId(), chunkIdx);
            return;
        }

        throw new BrokerException(ErrorCode.FILE_UPLOAD_FAILED);
    }

    private FileChunksMeta openFileChunksInfo(FileChunksMeta fileChunksMeta) throws BrokerException {
        ParamCheckUtils.validateFileName(fileChunksMeta.getFileName());
        ParamCheckUtils.validateFileSize(fileChunksMeta.getFileSize());
        ParamCheckUtils.validateFileMd5(fileChunksMeta.getFileMd5());

        // 1. create FileChunksMeta
        FileChunksMeta newFileChunksMeta;
        try {
            newFileChunksMeta = new FileChunksMeta(WeEventUtils.generateUuid(),
                    URLDecoder.decode(fileChunksMeta.getFileName(), StandardCharsets.UTF_8.toString()),
                    fileChunksMeta.getFileSize(),
                    fileChunksMeta.getFileMd5(),
                    fileChunksMeta.getTopic(),
                    fileChunksMeta.getGroupId(),
                    fileChunksMeta.isOverwrite());
        } catch (UnsupportedEncodingException e) {
            log.error("decode fileName error", e);
            throw new BrokerException(ErrorCode.DECODE_FILE_NAME_ERROR);
        }

        // 2. create AMOP channel with FileTransportSender
        FileChunksMeta remoteFileChunksMeta = this.fileTransportService.openChannel(newFileChunksMeta);
        return remoteFileChunksMeta;
    }


    private FileChunksMeta getFileChunksInfo(String topic, String groupId, String fileId) throws BrokerException {
        ParamCheckUtils.validateFileId(fileId);
        FileChunksMeta fileChunksMeta = fileTransportService.getReceiverFileChunksMeta(topic, groupId, fileId);

        return fileChunksMeta;
    }

    private boolean uploadChunk(FileChunksMeta local, int chunkIdx, byte[] chunkData) throws BrokerException {
        ParamCheckUtils.validateFileId(local.getFileId());
        ParamCheckUtils.validateChunkIdx(chunkIdx);
        ParamCheckUtils.validateChunkData(chunkData);

        fileTransportService.sendChunkData(local.getTopic(), local.getGroupId(), local.getFileId(), chunkIdx, chunkData);

        return true;
    }

    private FileChunksMeta closeChunk(FileChunksMeta local) throws BrokerException {
        ParamCheckUtils.validateFileId(local.getFileId());

        // close channel
        FileChunksMeta fileChunksMeta = this.fileTransportService.closeChannel(local.getTopic(), local.getFileId());

        return fileChunksMeta;
    }

    private boolean checkChunkFullUpload(RandomAccessFile f, FileChunksMeta local) {
        boolean isFullUpload = false;
        for (int i = 0; i <= CHUNK_RETRY_COUNT; i++) {
            try {
                FileChunksMeta fileChunksMeta = this.getFileChunksInfo(local.getTopic(), local.getGroupId(), local.getFileId());
                if (fileChunksMeta.checkChunkFull()) {
                    isFullUpload = true;
                    break;
                } else {
                    for (int k = 0; k < fileChunksMeta.getChunkNum(); k++) {
                        if (!fileChunksMeta.getChunkStatus().get(k)) {
                            this.uploadChunkDetails(f, fileChunksMeta, k);
                        }
                    }

                    if (i == CHUNK_RETRY_COUNT) {
                        fileChunksMeta = this.getFileChunksInfo(local.getTopic(), local.getGroupId(), local.getFileId());
                        isFullUpload = fileChunksMeta.checkChunkFull();
                    }
                }
            } catch (BrokerException | IOException e) {
                log.error("checkChunkFullUpload failed ,fileId:{} retry count: {}", local.getFileId(), i);
            }
        }
        return isFullUpload;
    }
}
