package com.webank.weevent.governance.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.commons.CommonsMultipartResolver;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.file.IWeEventFileClient;
import com.webank.weevent.file.dto.FileChunksMetaStatus;
import com.webank.weevent.file.dto.FileTransportStats;
import com.webank.weevent.file.inner.DiskFiles;
import com.webank.weevent.file.service.FileChunksMeta;
import com.webank.weevent.governance.GovernanceApplication;
import com.webank.weevent.governance.common.ConstantProperties;
import com.webank.weevent.governance.common.ErrorCode;
import com.webank.weevent.governance.common.GovernanceException;
import com.webank.weevent.governance.common.GovernanceResult;
import com.webank.weevent.governance.entity.FileChunksMetaEntity;
import com.webank.weevent.governance.entity.FileTransportChannelEntity;
import com.webank.weevent.governance.entity.FileTransportStatusEntity;
import com.webank.weevent.governance.entity.PeerInfoParam;
import com.webank.weevent.governance.entity.UploadChunkParam;
import com.webank.weevent.governance.repository.TransportChannelRepository;
import com.webank.weevent.governance.repository.TransportStatusRepository;
import com.webank.weevent.governance.utils.ParamCheckUtils;
import com.webank.weevent.governance.utils.Utils;

import javafx.util.Pair;
import lombok.extern.slf4j.Slf4j;

/**
 * file upload/download Service.
 *
 * @author v_wbhwliu
 * @version 1.3
 * @since 2020/5/20
 */
@Service
@Slf4j
public class FileService {

    private TransportChannelRepository transportChannelRepository;
    private TransportStatusRepository transportStatusRepository;
    // file upload root path
    private String uploadPath;
    // file download root path
    private String downloadPath;
    // <brokerId, <groupId, <IWeEventFileClient, DiskFiles>>>
    private final Map<String, IWeEventFileClient> fileClientMap = new ConcurrentHashMap<>();
    // <brokerId, <groupId, <topic, overwrite>>>
    private final Map<String, Boolean> transportMap = new ConcurrentHashMap<>();
    // upload local file to governance server, <fileId, FileChunksMeta>
    private final Map<String, Pair<FileChunksMeta, DiskFiles>> fileChunksMap = new ConcurrentHashMap<>();

    private final static String IDENTIFIER = "-";

    @Autowired
    public void setTransportChannelRepository(TransportChannelRepository transportChannelRepository) throws GovernanceException {
        this.transportChannelRepository = transportChannelRepository;
        this.uploadPath = GovernanceApplication.governanceConfig.getFileTransportPath() + File.separator + ConstantProperties.UPLOAD;
        this.downloadPath = GovernanceApplication.governanceConfig.getFileTransportPath() + File.separator + ConstantProperties.DOWNLOAD;
        this.initFileTransportBasePath();
        this.syncTransportToCache(this.transportChannelRepository.findAll());
    }

    @Autowired
    public void setTransportStatusRepository(TransportStatusRepository transportStatusRepository) {
        this.transportStatusRepository = transportStatusRepository;
    }

    public GovernanceResult<Boolean> openTransport(FileTransportChannelEntity fileTransport) throws GovernanceException {
        ParamCheckUtils.validateTransportName(fileTransport.getTopicName());
        ParamCheckUtils.validateTransportRole(fileTransport.getRole());
        ParamCheckUtils.validateOverWrite(fileTransport.getOverWrite());
        this.checkTransportExist(fileTransport);

        if (ConstantProperties.TRANSPORT_RECEIVER.equals(fileTransport.getRole())) {
            this.openTransport4Receiver(fileTransport);
        } else {
            this.openTransport4Sender(fileTransport);
        }

        this.transportChannelRepository.save(fileTransport);
        return GovernanceResult.ok(true);
    }

    public Set<PeerInfoParam> getSubscribers(FileTransportChannelEntity fileTransport) throws GovernanceException {
        IWeEventFileClient fileClient;
        try {
            Set<PeerInfoParam> params = new HashSet<>();
            fileClient = this.buildIWeEventFileClient(fileTransport);
            Set<String> set = fileClient.getSubscribers(fileTransport.getTopicName());
            for (String str : set) {
                PeerInfoParam param = new PeerInfoParam();
                param.setIpAndPort(str);
                params.add(param);
            }
            return params;
        } catch (BrokerException e) {
            log.error("get Subscribers failed.", e);
            throw new GovernanceException(e.getMessage());
        }
    }

    private void openTransport4Sender(FileTransportChannelEntity fileTransport) throws GovernanceException {
        IWeEventFileClient fileClient;
        try {
            fileClient = this.buildIWeEventFileClient(fileTransport);

            if (StringUtils.isBlank(fileTransport.getPublicKey())) {
                fileClient.openTransport4Sender(fileTransport.getTopicName());
            } else {
                fileClient.openTransport4Sender(fileTransport.getTopicName(), new ByteArrayInputStream(fileTransport.getPublicKey().getBytes(StandardCharsets.UTF_8)));
            }
        } catch (BrokerException e) {
            log.error("open sender transport failed.", e);
            String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                    IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());
            this.fileClientMap.remove(fileClientKey);
            throw new GovernanceException(e.getMessage());
        }

        //this.addIWeEventClientToCache(fileTransport, fileClient);
        this.addTransportToCache(fileTransport);
        log.info("open sender transport success, groupId:{}, topic:{}", fileTransport.getGroupId(), fileTransport.getTopicName());
    }

    private void openTransport4Receiver(FileTransportChannelEntity fileTransport) throws GovernanceException {
        IWeEventFileClient fileClient;
        try {
            fileClient = this.buildIWeEventFileClient(fileTransport);
            if (StringUtils.isBlank(fileTransport.getPrivateKey())) {
                fileClient.openTransport4Receiver(fileTransport.getTopicName(), new IWeEventFileClient.FileListener() {
                    @Override
                    public void onFile(String topicName, String fileName) {
                        log.info("receive file：{} from topic: {}.", fileName, topicName);
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("onException:", e);
                    }
                });
            } else {
                fileClient.openTransport4Receiver(fileTransport.getTopicName(), new IWeEventFileClient.FileListener() {
                    @Override
                    public void onFile(String topicName, String fileName) {
                        log.info("receive file：{} from topic: {}.", fileName, topicName);
                    }

                    @Override
                    public void onException(Throwable e) {
                        log.error("onException:", e);
                    }
                }, new ByteArrayInputStream(fileTransport.getPrivateKey().getBytes(StandardCharsets.UTF_8)));
            }
        } catch (BrokerException e) {
            log.error("open receive transport failed.", e);
            String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                    IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());
            this.fileClientMap.remove(fileClientKey);
            throw new GovernanceException(e.getMessage());
        }

        //this.addIWeEventClientToCache(fileTransport, fileClient);
        this.addTransportToCache(fileTransport);
        log.info("open receiver transport success, groupId:{}, topic:{}", fileTransport.getGroupId(), fileTransport.getTopicName());
    }

    public GovernanceResult<Boolean> uploadFile(HttpServletRequest request) throws GovernanceException {
        UploadChunkParam chunkParam = parseUploadChunkRequest(request);
        log.info("upload chunk:{}", chunkParam);
        FiscoConfig fiscoConfig = new FiscoConfig();
        fiscoConfig.load("");
        String nodeAddress = "";
        String convertNodeAddress = "";
        List<String> nodes = (List<String>) fiscoConfig.getConfigProperty().getNetwork().get("peers");
        for (String node : nodes) {
            convertNodeAddress += node;
            nodeAddress += convertNodeAddress(node);
        }
        chunkParam.getFileChunksMeta().setNodeAddress(convertNodeAddress);
        String topic = chunkParam.getFileChunksMeta().getTopic();

        String fileClientKey = chunkParam.getBrokerId() + IDENTIFIER + chunkParam.getFileChunksMeta().getGroupId() +
                IDENTIFIER + topic + IDENTIFIER + convertNodeAddress(nodeAddress);
        IWeEventFileClient fileClient = this.getIWeEventFileClient(fileClientKey);
        boolean isSuccess = this.uploadChunks(chunkParam.getFileChunksMeta(), chunkParam.getChunkNumber(), chunkParam.getChunkData());

        // if chunk upload failed, sleep and retry again
        for (int i = 1; i <= ConstantProperties.UPLOAD_CHUNK_FAIL_RETRY_COUNT; i++) {
            if (isSuccess) {
                break;
            }
            isSuccess = this.uploadChunks(chunkParam.getFileChunksMeta(), chunkParam.getChunkNumber(), chunkParam.getChunkData());
        }
        if (!isSuccess) {
            throw new GovernanceException(ErrorCode.FILE_UPLOAD_FAILED);
        }

        if (chunkParam.getFileChunksMeta().checkChunkFull()) {
            CompletableFuture.runAsync(() -> {
                String fileId = chunkParam.getFileChunksMeta().getFileId();
                String filePath = this.uploadPath.concat(File.separator).concat(fileId).concat(File.separator)
                        .concat(chunkParam.getFileChunksMeta().getGroupId()).concat(File.separator)
                        .concat(convertNodeAddress(chunkParam.getFileChunksMeta().getNodeAddress())).concat(File.separator)
                        .concat(topic).concat(File.separator)
                        .concat(chunkParam.getFileChunksMeta().getFileName());

                boolean overWrite = this.transportMap.get(fileClientKey);

                FileTransportStatusEntity status = addFileTransportRecord(chunkParam);

                try {
                    log.info("all chunks has uploaded success, start publish file, filePath:{}", filePath);
                    fileClient.publishFile(topic, filePath, overWrite);
                    log.info("publish file success, topic:{}, fileName:{}.", topic, chunkParam.getFileChunksMeta().getFileName());
                    transportStatusRepository.updateTransportStatus(ConstantProperties.SUCCESS, status.getId().longValue());
                } catch (BrokerException | IOException e) {
                    log.error("publish file error, fileName:{}.", chunkParam.getFileChunksMeta().getFileName(), e);
                    transportStatusRepository.updateTransportStatus(ConstantProperties.FAILED, status.getId().longValue());
                } finally {
                    // remove local file after publish
                    Utils.removeLocalFile(this.uploadPath.concat(File.separator).concat(fileId));
                    this.fileChunksMap.remove(fileId);
                    log.info("remove local file after publish, fileName:topic:{}, fileName:{}.", topic, chunkParam.getFileChunksMeta().getFileName());
                }
            });
        }
        return GovernanceResult.ok(true);
    }

    private FileTransportStatusEntity addFileTransportRecord(UploadChunkParam chunkParam) {
        FileTransportStatusEntity status = transportStatusRepository.queryByBrokerIdAndGroupIdAndTopicNameAndFileName(
                chunkParam.getBrokerId(), chunkParam.getFileChunksMeta().getGroupId(),
                chunkParam.getFileChunksMeta().getTopic(), chunkParam.getFileChunksMeta().getFileName());

        if (Objects.isNull(status)) {
            status = new FileTransportStatusEntity();
            status.setBrokerId(chunkParam.getBrokerId());
            status.setGroupId(chunkParam.getFileChunksMeta().getGroupId());
            status.setTopicName(chunkParam.getFileChunksMeta().getTopic());
            status.setNodeAddress(chunkParam.getFileChunksMeta().getNodeAddress());
            status.setFileName(chunkParam.getFileChunksMeta().getFileName());
            status.setFileMD5(chunkParam.getFileId());
            status.setFileSize(chunkParam.getFileChunksMeta().getFileSize());
            status.setStatus(ConstantProperties.UPLOADING);
            transportStatusRepository.save(status);
        } else {
            transportStatusRepository.updateTransportStatus(ConstantProperties.UPLOADING, status.getId().longValue());
        }
        return status;
    }

    private UploadChunkParam parseUploadChunkRequest(HttpServletRequest request) throws GovernanceException {
        UploadChunkParam chunkParam = new UploadChunkParam();
        String fileId = request.getParameter("identifier");
        FileChunksMeta fileChunksMeta = this.fileChunksMap.get(fileId).getKey();

        chunkParam.setFileChunksMeta(fileChunksMeta);
        chunkParam.setBrokerId(Integer.parseInt(request.getParameter("brokerId")));
        chunkParam.setChunkNumber(Integer.parseInt(request.getParameter("chunkNumber")) - 1);
        chunkParam.setFileId(fileId);

        CommonsMultipartResolver multipartResolver = new CommonsMultipartResolver(request.getSession().getServletContext());
        if (multipartResolver.isMultipart(request)) {
            MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
            Iterator<String> iter = multiRequest.getFileNames();
            while (iter.hasNext()) {
                MultipartFile file = multiRequest.getFile(iter.next());
                if (!Objects.isNull(file)) {
                    try {
                        chunkParam.setChunkData(file.getBytes());
                    } catch (IOException e) {
                        log.error("parse upload chunk data error.", e);
                        throw new GovernanceException(ErrorCode.PARSE_CHUNK_REQUEST_ERROR);
                    }
                }
            }
        }
        return chunkParam;
    }

    public String downloadFile(String groupId, String topic, String fileName, String nodeAddress) throws GovernanceException {
        nodeAddress = convertNodeAddress(nodeAddress);
        String filePath = this.downloadPath.concat(File.separator).concat(groupId).concat(File.separator).concat(nodeAddress).concat(File.separator).concat(topic).concat(File.separator).concat(fileName);
        if (filePath.indexOf("..") != -1) {
            log.error("file path not exist .., topic:{}, fileName:{}, filePath:{}", topic, fileName, filePath);
            throw new GovernanceException(ErrorCode.FILE_NOT_EXIST);
        }
        if (!new File(filePath).exists()) {
            log.error("file not exist, topic:{}, fileName:{}", topic, fileName);
            throw new GovernanceException(ErrorCode.FILE_NOT_EXIST);
        }
        return filePath;
    }

    public GovernanceResult<List<FileChunksMeta>> listFile(String groupId, Integer brokerId, String topic, String nodeAddress) throws GovernanceException {
        String fileClientKey = brokerId + IDENTIFIER + groupId + IDENTIFIER + topic +
                IDENTIFIER + convertNodeAddress(nodeAddress);
        IWeEventFileClient fileClient = getIWeEventFileClient(fileClientKey);
        try {
            List<FileChunksMeta> fileChunksMetas = fileClient.listFiles(groupId, topic);
            return GovernanceResult.ok(fileChunksMetas);
        } catch (BrokerException e) {
            log.error("list file error, topic:{}", topic);
            throw new GovernanceException(e.getMessage());
        }
    }

    public GovernanceResult<List<FileChunksMetaEntity>> downLoadStatus(String groupId, Integer brokerId, String topic, String nodeAddress) throws GovernanceException {
        List<FileChunksMetaStatus> fileChunksMetaStatusList = null;
        List<FileChunksMetaEntity> chunksMetaEntities = new ArrayList<FileChunksMetaEntity>();
        String fileClientKey = brokerId + IDENTIFIER + groupId + IDENTIFIER + topic +
                IDENTIFIER + convertNodeAddress(nodeAddress);
        IWeEventFileClient fileClient = this.getIWeEventFileClient(fileClientKey);
        FileTransportStats status = fileClient.status(topic);
        if (status.getReceiver().containsKey(groupId)) {
            fileChunksMetaStatusList = status.getReceiver().get(groupId).get(topic);
            fileChunksMetaStatusList = null == fileChunksMetaStatusList ? new ArrayList<FileChunksMetaStatus>() : fileChunksMetaStatusList;
            for (FileChunksMetaStatus fileChunksMetaStatus : fileChunksMetaStatusList) {
                FileChunksMeta chunksMeta = fileChunksMetaStatus.getFile();
                FileChunksMetaEntity fileChunksMetaEntity = new FileChunksMetaEntity();
                BeanUtils.copyProperties(chunksMeta, fileChunksMetaEntity);
                if (Objects.equals(fileChunksMetaStatus.getProcess(), "100.00%")) {
                    fileChunksMetaEntity.setStatus("1");
                } else {
                    fileChunksMetaEntity.setStatus("3");
                }
                BeanUtils.copyProperties(fileChunksMetaStatus, fileChunksMetaEntity);
                chunksMetaEntities.add(fileChunksMetaEntity);
            }
        }
        return GovernanceResult.ok(chunksMetaEntities);
    }

    public GovernanceResult<List<FileTransportStatusEntity>> uploadStatus(String groupId, Integer brokerId, String topic, String nodeAddress) throws GovernanceException {
        List<FileTransportStatusEntity> fileTransportStatusList = this.transportStatusRepository.queryByBrokerIdAndGroupIdAndNodeAddressAndTopicName(brokerId, groupId, nodeAddress, topic);

        String fileClientKey = brokerId + IDENTIFIER + groupId + IDENTIFIER + topic + IDENTIFIER + convertNodeAddress(nodeAddress);

        IWeEventFileClient fileClient = this.getIWeEventFileClient(fileClientKey);
        FileTransportStats status = fileClient.status(topic);
        if (status.getSender().containsKey(groupId)) {
            List<FileChunksMetaStatus> fileChunksMetaStatusList = status.getSender().get(groupId).get(topic);
            fileTransportStatusList.forEach(fileTransportStatusEntity -> fileChunksMetaStatusList.forEach(fileChunksMetaStatus -> {
                log.info("fileChunksMetaStatus.getSpeed():=" + fileChunksMetaStatus.getSpeed());
                if (Objects.equals(fileChunksMetaStatus.getFile().getFileName(), fileTransportStatusEntity.getFileName())) {
                    BeanUtils.copyProperties(fileChunksMetaStatus, fileTransportStatusEntity);
                }
                if (Objects.equals(fileTransportStatusEntity.getStatus(), ConstantProperties.SUCCESS)) {
                    fileTransportStatusEntity.setProcess("100%");
                } else {
                    String speed = fileChunksMetaStatus.getSpeed();
                    this.transportStatusRepository.updateTransportSpeed(speed, fileTransportStatusEntity.getId().longValue());
                }
            }));
            for (FileTransportStatusEntity fileTransportStatusEntity : fileTransportStatusList) {
                if (Objects.equals(fileTransportStatusEntity.getStatus(), ConstantProperties.SUCCESS)) {
                    fileTransportStatusEntity.setProcess("100%");
                }
            }
        }
        return GovernanceResult.ok(fileTransportStatusList);
    }

    public GovernanceResult<List<FileTransportChannelEntity>> listTransport(String groupId, Integer brokerId) {
        List<FileTransportChannelEntity> fileTransportList = this.transportChannelRepository.queryByBrokerIdAndGroupId(brokerId, groupId);
        fileTransportList.forEach(fileTransport -> {
            fileTransport.setCreateTime(Utils.dateToStr(fileTransport.getCreateDate()));
            if (StringUtils.isNotBlank(fileTransport.getPublicKey()) || StringUtils.isNotBlank(fileTransport.getPrivateKey())) {
                fileTransport.setVerified(true);
            }

        });
        log.info("get transport list success, transport.size:{}", fileTransportList.size());
        return GovernanceResult.ok(fileTransportList);
    }

    public GovernanceResult<Boolean> closeTransport(FileTransportChannelEntity fileTransport) throws GovernanceException {
        String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());

        IWeEventFileClient fileClient = this.getIWeEventFileClient(fileClientKey);
        fileClient.closeTransport(fileTransport.getTopicName());

        this.transportChannelRepository.delete(fileTransport);
        this.removeTransportCache(fileTransport);
        return GovernanceResult.ok(true);
    }

    public GovernanceResult<List<Integer>> prepareUploadFile(String fileId, String filename, String topic, String groupId,
                                                             long totalSize, Integer chunkSize) throws GovernanceException {
        if (this.fileChunksMap.containsKey(fileId)) {
            FileChunksMeta fileChunksMeta = this.fileChunksMap.get(fileId).getKey();
            return GovernanceResult.ok(this.chunkUploadedList(fileChunksMeta));
        }

        FileChunksMeta fileChunksMeta = new FileChunksMeta(fileId, filename, totalSize, "", topic, groupId, true);
        fileChunksMeta.initChunkSize(chunkSize);
        String filePath = this.uploadPath + File.separator + fileId;
        if (filePath.indexOf("..") != -1) {
            log.info("file path not exist.. filePath, {}", filePath);
            throw new GovernanceException(ErrorCode.FILE_NOT_EXIST);
        }
        DiskFiles diskFiles = new DiskFiles(filePath);
        try {
            diskFiles.createFixedLengthFile(fileChunksMeta);
            diskFiles.saveFileMeta(fileChunksMeta);
        } catch (BrokerException e) {
            log.error("create fileChunksMeta error.fileName:{}, fileId:{}.", filename, fileId, e);
            throw new GovernanceException(e.getMessage());
        }
        this.fileChunksMap.put(fileId, new Pair<>(fileChunksMeta, diskFiles));

        return GovernanceResult.ok(chunkUploadedList(fileChunksMeta));
    }

    public String genPemFile(String groupId, Integer brokerId, String topicName, String nodeAddress) throws GovernanceException {
        String fileClientKey = brokerId + IDENTIFIER + groupId + IDENTIFIER + topicName +
                IDENTIFIER + convertNodeAddress(nodeAddress);
        IWeEventFileClient fileClient = getIWeEventFileClient(fileClientKey);
        try {
            return fileClient.genPemFile();
        } catch (BrokerException e) {
            log.error("genPemFile error, pemPath:{}.", e);
            throw new GovernanceException(ErrorCode.GENERATE_PEM_FAILED);
        }
    }

    public GovernanceResult<Object> checkFileIsUploaded(String groupId, Integer brokerId, String topic, String fileName, String nodeAddress) throws GovernanceException {
        ParamCheckUtils.validateTransportName(topic);
        ParamCheckUtils.validateFileName(fileName);

        String fileClientKey = brokerId + IDENTIFIER + groupId + IDENTIFIER + topic + IDENTIFIER + convertNodeAddress(nodeAddress);

        IWeEventFileClient fileClient = getIWeEventFileClient(fileClientKey);
        try {
            boolean fileExist = fileClient.isFileExist(fileName, topic, groupId);
            if (fileExist) {
                return GovernanceResult.build(ErrorCode.TRANSPORT_ALREADY_EXISTS.getCode(), "the file is already uploaded", true);
            }
            return GovernanceResult.ok(false);
        } catch (BrokerException e) {
            log.error("check file is uploaded, topic:{}, fileName:{}", topic, fileName, e);
            throw new GovernanceException(ErrorCode.CHECK_FILE_IS_UPLOADED_ERROR);
        }
    }

    private IWeEventFileClient buildIWeEventFileClient(FileTransportChannelEntity fileTransport) {
        FiscoConfig fiscoConfig = new FiscoConfig();
        fiscoConfig.load("");
        String groupId = fileTransport.getGroupId();

        List<String> nodes = new ArrayList<String>();
        String[] nodeAddress = fileTransport.getNodeAddress().split(",");
        for (String node : nodeAddress) {
            nodes.add(node);
        }
        fiscoConfig.setFiscoNodes(nodes);

        String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());
        if (!this.fileClientMap.containsKey(fileClientKey)) {
            IWeEventFileClient fileClient = IWeEventFileClient.build(groupId, this.downloadPath, ConstantProperties.FILE_CHUNK_SIZE, fiscoConfig);

            this.fileClientMap.put(fileClientKey, fileClient);
        }
        return this.fileClientMap.get(fileClientKey);
    }

    private IWeEventFileClient getIWeEventFileClient(String fileClientKey) throws GovernanceException {
        if (!this.fileClientMap.containsKey(fileClientKey)) {
            throw new GovernanceException(ErrorCode.TRANSPORT_NOT_EXISTS);
        }
        return this.fileClientMap.get(fileClientKey);
    }

    private boolean uploadChunks(FileChunksMeta fileChunksMeta, Integer chunkIdx, byte[] chunkData) {
        DiskFiles diskFiles = this.fileChunksMap.get(fileChunksMeta.getFileId()).getValue();
        try {
            diskFiles.writeChunkData(fileChunksMeta.getFileId(), chunkIdx, chunkData);
            this.fileChunksMap.get(fileChunksMeta.getFileId()).getKey().getChunkStatus().set(chunkIdx);
            log.info("upload file chunk data success, {}@{}.", fileChunksMeta.getFileId(), chunkIdx);
        } catch (BrokerException e) {
            log.error("write chunk data error, topic:{}, {}@{}", fileChunksMeta.getTopic(), fileChunksMeta.getFileId(), chunkIdx, e);
            return false;
        }
        return true;
    }

    private void checkTransportExist(FileTransportChannelEntity fileTransport) throws GovernanceException {
        String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());

        if (this.transportMap.containsKey(fileClientKey)) {
            throw new GovernanceException(ErrorCode.TRANSPORT_ALREADY_EXISTS);
        }
    }

    private void syncTransportToCache(List<FileTransportChannelEntity> transportList) throws GovernanceException {
        for (FileTransportChannelEntity fileTransport : transportList) {
            try {
                checkTransportExist(fileTransport);
                if (ConstantProperties.TRANSPORT_RECEIVER.equals(fileTransport.getRole())) {
                    this.openTransport4Receiver(fileTransport);
                } else {
                    this.openTransport4Sender(fileTransport);
                }
            } catch (GovernanceException e) {
                if (e.getCode() != ErrorCode.TRANSPORT_ALREADY_EXISTS.getCode()) {
                    throw e;
                }
            }
        }
        log.info("synchronize transport from db to local cache success.");
    }

    private void initFileTransportBasePath() {
        File uploadFile = new File(this.uploadPath);
        if (!uploadFile.exists()) {
            uploadFile.mkdir();
        }
        File downloadFile = new File(this.downloadPath);
        if (!downloadFile.exists()) {
            downloadFile.mkdir();
        }
    }

    private void addTransportToCache(FileTransportChannelEntity fileTransport) {
        String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());

        // 0 means false, 1 means true;
        boolean isOverWrite = "1".equals(fileTransport.getOverWrite());
        this.transportMap.put(fileClientKey, isOverWrite);
    }

    private void removeTransportCache(FileTransportChannelEntity fileTransport) {
        String fileClientKey = fileTransport.getBrokerId() + IDENTIFIER + fileTransport.getGroupId() +
                IDENTIFIER + fileTransport.getTopicName() + IDENTIFIER + convertNodeAddress(fileTransport.getNodeAddress());
        if (Objects.isNull(this.transportMap.get(fileClientKey))) {
            log.error("transport:{} not exists in local cache.", fileClientKey);
        } else {
            this.transportMap.remove(fileClientKey);
        }
    }

    private List<Integer> chunkUploadedList(FileChunksMeta fileChunksMeta) {
        List<Integer> uploadedChunks = new ArrayList<>();
        for (int i = 0; i < fileChunksMeta.getChunkNum(); i++) {
            if (fileChunksMeta.getChunkStatus().get(i)) {
                uploadedChunks.add(i + 1);
            }
        }
        return uploadedChunks;
    }

    private static String convertNodeAddress(String nodeAddress) {
        return nodeAddress.replace(".", "").replace(",", "").replace(":", "");
    }
}
