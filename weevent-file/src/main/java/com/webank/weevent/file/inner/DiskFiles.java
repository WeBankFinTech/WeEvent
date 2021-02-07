package com.webank.weevent.file.inner;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.JsonHelper;
import com.webank.weevent.file.service.FileChunksMeta;

import lombok.extern.slf4j.Slf4j;

/**
 * Files stored in local disk.
 *
 * @author matthewliu
 * @since 2020/02/18
 */
@Slf4j
public class DiskFiles {
    private static final String PATH_SEPARATOR = "/";
    public final String MetaFileSuffix = ".json";
    private final String path;
    private Map<String, FileChunksMeta> fileIdChunksMeta = new ConcurrentHashMap<>();

    public DiskFiles(String path) {
        File localPath = new File(path);
        if (!localPath.exists()) {
            boolean result = localPath.mkdirs();
            if (result) {
                log.info("create local file path, {}", path);
            }
        }

        log.info("local file path: {}, {} -> {}", path, localPath.getFreeSpace(), localPath.getTotalSpace());
        this.path = path;
    }

    public String genLocalFileName(String fileId) throws BrokerException {
        FileChunksMeta fileChunksMeta = fileIdChunksMeta.get(fileId);
        if (fileChunksMeta == null) {
            log.error("the fileChunksMeta corresponding to fieldId not exist, {}", fileId);
            throw new BrokerException(ErrorCode.FILE_GEN_LOCAL_FILE_NAME_FAILED);
        }
        String nodeAddress = fileChunksMeta.getNodeAddress().replace(".", "").replace(":", "").replace(",", "");
        return this.path + PATH_SEPARATOR + fileChunksMeta.getGroupId() + PATH_SEPARATOR + nodeAddress + PATH_SEPARATOR + fileChunksMeta.getTopic() + PATH_SEPARATOR + fileChunksMeta.getFileName();
    }

    private String genLocalMetaFileName(String fileId) throws BrokerException {
        return this.genLocalFileName(fileId) + MetaFileSuffix;
    }

    // save FileChunksMeta in UTF-8 format, fileName may be contain chinese character
    public FileChunksMeta loadFileMeta(String fileId) throws BrokerException {
        String localMetaFile = this.genLocalMetaFileName(fileId);
        File fileMeta = new File(localMetaFile);
        if (!fileMeta.exists()) {
            log.error("not exist local meta file, {}", localMetaFile);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        }
        return this.loadFileMeta(fileMeta);
    }

    public FileChunksMeta loadFileMeta(File fileMeta) throws BrokerException {
        if (fileMeta.length() > 1024 * 1024) {
            log.error("local meta file is exceed 1M, skip it");
            throw new BrokerException(ErrorCode.FILE_READ_EXCEPTION);
        }

        try (FileInputStream fileInputStream = new FileInputStream(fileMeta);
             InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8)) {
            char[] data = new char[(int) fileMeta.length()];
            int readSize = reader.read(data);
            // the read size is length of UTF-8 char, less then fileMeta.length()
            if (readSize <= 0) {
                log.error("read local meta file failed");
                throw new BrokerException(ErrorCode.FILE_READ_EXCEPTION);
            }
            return JsonHelper.json2Object(String.valueOf(data), FileChunksMeta.class);
        } catch (IOException | BrokerException e) {
            log.error("read local meta file exception", e);
            throw new BrokerException(ErrorCode.FILE_READ_EXCEPTION);
        }
    }

    public void saveFileMeta(FileChunksMeta fileChunksMeta) throws BrokerException {
        log.info("save FileChunksMeta, filled chunk: {} -> chunkNum: {}", fileChunksMeta.getChunkStatus().cardinality(), fileChunksMeta.getChunkNum());

        String localMetaFile = this.genLocalMetaFileName(fileChunksMeta.getFileId());
        try (FileOutputStream fileOutputStream = new FileOutputStream(localMetaFile);
             OutputStreamWriter writer = new OutputStreamWriter(fileOutputStream, StandardCharsets.UTF_8)) {
            String data = JsonHelper.object2Json(fileChunksMeta);
            writer.write(data);
        } catch (IOException | BrokerException e) {
            log.error("write local meta file exception", e);
            throw new BrokerException(ErrorCode.FILE_WRITE_EXCEPTION);
        }
    }

    public void createFixedLengthFile(FileChunksMeta fileChunksMeta) throws BrokerException {
        // ensure path exist and disk space
        String nodeAddress = fileChunksMeta.getNodeAddress().replace(".", "").replace(":", "").replace(",", "");
        String filePath = this.path + PATH_SEPARATOR + fileChunksMeta.getGroupId() + PATH_SEPARATOR + nodeAddress + PATH_SEPARATOR + fileChunksMeta.getTopic();
        File path = new File(filePath);
        path.mkdirs();
        if (!path.exists()) {
            log.error("not exist local file path, {}", filePath);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST_PATH);
        } else {
            // This file exists in the directory, and do not allow overwriting
            if (!fileChunksMeta.isOverwrite()) {
                File[] fileList = path.listFiles();
                if (fileList != null) {
                    for (File file : fileList) {
                        if (file.getName().equals(fileChunksMeta.getFileName())) {
                            log.error("file exists in the directory, and do not allow overwrite, filename:{}", fileChunksMeta.getFileName());
                            throw new BrokerException(ErrorCode.FILE_EXIST_AND_NOT_ALLOW_OVERWRITE);
                        }
                    }
                }
            }
        }

        if (path.getFreeSpace() < fileChunksMeta.getFileSize() + 1024 * 1024) {
            log.error("not enough disk space, {} -> free {}", fileChunksMeta.getFileSize(), path.getFreeSpace());
            throw new BrokerException(ErrorCode.FILE_NOT_ENOUGH_SPACE);
        }

        // record fileId - FileChunksMeta
        this.fileIdChunksMeta.put(fileChunksMeta.getFileId(), fileChunksMeta);

        String localFile = this.genLocalFileName(fileChunksMeta.getFileId());
        log.info("create local file for receiving file, {} size: {}", localFile, fileChunksMeta.getFileSize());
        try (RandomAccessFile file = new RandomAccessFile(localFile, "rw")) {
            file.setLength(fileChunksMeta.getFileSize());
        } catch (IOException e) {
            log.error("create fixed length file failed", e);
            throw new BrokerException(ErrorCode.FILE_WRITE_EXCEPTION);
        }
    }

    public synchronized void writeChunkData(String fileId, int chunkIndex, byte[] chunkData) throws BrokerException {

        // believe FileChunksMeta in local file
        FileChunksMeta fileChunksMeta = this.loadFileMeta(fileId);
        if (chunkIndex >= fileChunksMeta.getChunkNum()) {
            log.error("invalid chunk meta data, skip");
            throw new BrokerException(ErrorCode.FILE_INVALID_CHUNK);
        }
        if (chunkData.length > fileChunksMeta.getChunkSize()) {
            log.error("invalid chunk meta data, skip");
            throw new BrokerException(ErrorCode.FILE_INVALID_CHUNK);
        }

        String localFile = this.genLocalFileName(fileId);
        log.info("write data in local file, {}@{} size: {}", localFile, chunkIndex, chunkData.length);
        try (RandomAccessFile f = new RandomAccessFile(localFile, "rw")) {
            f.seek((long) chunkIndex * fileChunksMeta.getChunkSize());
            f.write(chunkData);
        } catch (FileNotFoundException e) {
            log.error("not exist local file", e);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        } catch (IOException e) {
            log.error("write local file failed", e);
            throw new BrokerException(ErrorCode.FILE_WRITE_EXCEPTION);
        }

        String localMetaFile = this.genLocalMetaFileName(fileId);
        log.info("update FileChunksMeta in local file, {}@{}", localMetaFile, chunkIndex);
        fileChunksMeta.getChunkStatus().set(chunkIndex);
        saveFileMeta(fileChunksMeta);
    }

    public byte[] readChunkData(String fileId, int chunkIndex) throws BrokerException {
        // load FileChunksMeta from local file, FileChunksMeta in memory is closed within amop chanel
        FileChunksMeta fileChunksMeta = this.loadFileMeta(fileId);
        if (chunkIndex >= fileChunksMeta.getChunkNum()) {
            log.error("invalid chunk meta data, skip");
            throw new BrokerException(ErrorCode.FILE_INVALID_CHUNK);
        }
        if (!fileId.equals(fileChunksMeta.getFileId())) {
            log.error("invalid chunk meta data, skip");
            throw new BrokerException(ErrorCode.FILE_INVALID_CHUNK);
        }

        // read data from local file
        String localFile = this.genLocalFileName(fileId);
        try (RandomAccessFile f = new RandomAccessFile(localFile, "r")) {
            int size = fileChunksMeta.getChunkSize();
            if (chunkIndex == fileChunksMeta.getChunkNum() - 1) {
                size = (int) (fileChunksMeta.getFileSize() % fileChunksMeta.getChunkSize());
            }
            byte[] data = new byte[size];
            f.seek((long) chunkIndex * fileChunksMeta.getChunkSize());
            int readSize = f.read(data);
            if (size != readSize) {
                log.error("read data from local file failed");
                throw new BrokerException(ErrorCode.FILE_READ_EXCEPTION);
            }
            return data;
        } catch (FileNotFoundException e) {
            log.error("not exist local file", e);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        } catch (IOException e) {
            log.error("read data from local file exception", e);
            throw new BrokerException(ErrorCode.FILE_READ_EXCEPTION);
        }
    }

    private void deleteFile(String fileName) {
        File file = new File(fileName);
        if (file.exists()) {
            boolean result = file.delete();
            log.info("delete file, result: {} {}", result, fileName);
        }
    }

    public void cleanUp(String fileId) throws BrokerException {
        this.deleteFile(this.genLocalFileName(fileId));
        this.deleteFile(this.genLocalMetaFileName(fileId));
    }

    public List<FileChunksMeta> listNotCompleteFiles(boolean all, String groupId, String topicName, String nodeAddress) {
        List<FileChunksMeta> fileChunksMetas = new ArrayList<>();

        nodeAddress = nodeAddress.replace(".", "").replace(":", "").replace(",", "");
        String filePath = this.path + PATH_SEPARATOR + groupId + PATH_SEPARATOR + nodeAddress + PATH_SEPARATOR + topicName;
        File topPath = new File(filePath);
        topPath.mkdirs();
        if (!topPath.exists()) {
            log.error("not exist path: {}", filePath);
            return fileChunksMetas;
        }

        File[] files = topPath.listFiles((dir, name) -> name.endsWith(MetaFileSuffix));
        if (files != null) {
            for (File file : files) {
                try {
                    FileChunksMeta fileChunksMeta = this.loadFileMeta(file);
                    if (all || !fileChunksMeta.checkChunkFull()) {
                        fileChunksMetas.add(fileChunksMeta);
                    }
                } catch (BrokerException e) {
                    log.error("load file meta failed, skip");
                }
            }
        }

        return fileChunksMetas;
    }

    public boolean checkFileExist(FileChunksMeta fileChunksMeta) throws BrokerException {
        // ensure path exist and disk space
        String filePath = this.path + PATH_SEPARATOR + fileChunksMeta.getGroupId() + PATH_SEPARATOR + fileChunksMeta.getTopic() + PATH_SEPARATOR + fileChunksMeta.getFileName();
        File file = new File(filePath);
        return file.exists();
    }
}
