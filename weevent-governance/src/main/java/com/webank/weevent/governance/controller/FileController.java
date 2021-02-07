package com.webank.weevent.governance.controller;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.webank.weevent.file.service.FileChunksMeta;
import com.webank.weevent.governance.common.GovernanceException;
import com.webank.weevent.governance.common.GovernanceResult;
import com.webank.weevent.governance.entity.FileChunksMetaEntity;
import com.webank.weevent.governance.entity.FileTransportChannelEntity;
import com.webank.weevent.governance.entity.FileTransportStatusEntity;
import com.webank.weevent.governance.entity.PeerInfoParam;
import com.webank.weevent.governance.service.FileService;
import com.webank.weevent.governance.utils.ParamCheckUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * file upload/download Controller.
 *
 * @author v_wbhwliu
 * @version 1.3
 * @since 2020/5/20
 */

@Slf4j
@CrossOrigin
@RequestMapping(value = "/file")
@Controller
public class FileController {

    private FileService fileService;

    @Autowired
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(path = "/openTransport")
    @ResponseBody
    public GovernanceResult<Boolean> openTransport(@RequestBody FileTransportChannelEntity fileTransport) throws GovernanceException {
        log.info("openTransport, fileTransport:{}.", fileTransport.toString());
        return this.fileService.openTransport(fileTransport);
    }
    
    @PostMapping(path = "/getSubscribers")
    @ResponseBody
    public GovernanceResult<Set<PeerInfoParam>> getSubscribers(@RequestBody FileTransportChannelEntity fileTransport) throws GovernanceException {
        log.info("getSubscribers, getSubscribers:{}.", fileTransport.toString());
        return new GovernanceResult<>(this.fileService.getSubscribers(fileTransport));
    }

    @PostMapping(path = "/upload")
    @ResponseBody
    public GovernanceResult<Boolean> uploadChunk(HttpServletRequest request) throws GovernanceException {
        return this.fileService.uploadFile(request);
    }

    @GetMapping(path = "/upload")
    @ResponseBody
    public GovernanceResult<List<Integer>> prepareUploadFile(@RequestParam(name = "groupId") String groupId,
                                                             @RequestParam(name = "identifier") String fileId,
                                                             @RequestParam(name = "topicName") String topicName,
                                                             @RequestParam(name = "totalChunks") Integer totalChunks,
                                                             @RequestParam(name = "totalSize") long totalSize,
                                                             @RequestParam(name = "chunkSize") Integer chunkSize,
                                                             @RequestParam(name = "filename") String filename,
                                                             @RequestParam(name = "nodeAddress") String nodeAddress,
                                                             @RequestParam(name = "role") String role) throws GovernanceException {
        log.info("prepareUploadFile, groupId:{}, fileId:{}, filename:{}, topic:{}, totalSize:{}, totalChunks:{}, nodeAddress:{}, role:{}",
                groupId, fileId, filename, topicName, totalSize, totalChunks, nodeAddress, role);
        return this.fileService.prepareUploadFile(fileId, filename, topicName, groupId, totalSize, chunkSize, nodeAddress, role);
    }

    @GetMapping(path = "/download")
    public void download(@RequestParam(name = "groupId") String groupId,
                         @RequestParam(name = "topic") String topic,
                         @RequestParam(name = "fileName") String fileName,
                         @RequestParam(name = "nodeAddress") String nodeAddress,
                         HttpServletResponse response) throws GovernanceException {
        log.info("download file, topic:{}, fileName:{}, nodeAddress{}.", topic, fileName ,nodeAddress);
        response.setHeader("content-type", "application/octet-stream");
        response.setContentType("application/octet-stream; charset=UTF-8");

        ParamCheckUtils.validateFileName(fileName);
        String downloadFile = this.fileService.downloadFile(groupId, topic, fileName, nodeAddress);
        if (StringUtils.isBlank(downloadFile)) {
            throw new GovernanceException("download file not exist");
        }
        try {
            response.setHeader("filename", URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
        } catch (UnsupportedEncodingException e) {
            log.error("encode fileName error, fileName:{}", fileName, e);
        }

        byte[] buffer = new byte[1024];

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(downloadFile));
             OutputStream os = response.getOutputStream()) {
            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                os.flush();
                i = bis.read(buffer);
            }
            log.info("download file success, topic:{}, fileName:{}", topic, fileName);
        } catch (IOException e) {
            log.error("download file error, topic:{} fileName:{}", topic, fileName, e);
            throw new GovernanceException("download file error", e);
        }
    }

    @GetMapping(path = "/listFile")
    @ResponseBody
    public GovernanceResult<List<FileChunksMeta>> listFile(@RequestParam(name = "groupId") String groupId,
                                                           @RequestParam(name = "brokerId") Integer brokerId,
                                                           @RequestParam(name = "topicName") String topicName,
                                                           @RequestParam(name = "nodeAddress") String nodeAddress
                                                           ) throws GovernanceException {
        log.info("listFile, groupId:{}, topic:{}, nodeAddress{}.", groupId, topicName, nodeAddress);
        return this.fileService.listFile(groupId, brokerId, topicName, nodeAddress);
    }

    @GetMapping(path = "/downLoadStatus")
    @ResponseBody
    public GovernanceResult<List<FileChunksMetaEntity>> downLoadStatus(@RequestParam(name = "groupId") String groupId,
                                                                       @RequestParam(name = "brokerId") Integer brokerId,
                                                                       @RequestParam(name = "topicName") String topicName,
                                                                       @RequestParam(name = "nodeAddress") String nodeAddress
                                                                       ) throws GovernanceException {
        log.info("status, groupId:{}, topic:{}, nodeAddress:{}.", groupId, topicName, nodeAddress);
        return this.fileService.downLoadStatus(groupId, brokerId, topicName, nodeAddress);
    }

    @GetMapping(path = "/uploadStatus")
    @ResponseBody
    public GovernanceResult<List<FileTransportStatusEntity>> uploadStatus(@RequestParam(name = "groupId") String groupId,
                                                                          @RequestParam(name = "brokerId") Integer brokerId,
                                                                          @RequestParam(name = "topicName") String topicName,
                                                                          @RequestParam(name = "nodeAddress") String nodeAddress) throws GovernanceException {
        log.info("status, groupId:{}, topic:{}, nodeAddress:{}, role:{}.", groupId, topicName, nodeAddress);
        return this.fileService.uploadStatus(groupId, brokerId, topicName, nodeAddress);
    }

    @GetMapping(path = "/listTransport")
    @ResponseBody
    public GovernanceResult<List<FileTransportChannelEntity>> listTransport(@RequestParam(name = "groupId") String groupId,
                                                                            @RequestParam(name = "brokerId") Integer brokerId) {
        log.info("listTransport, groupId:{}, brokerId:{}.", groupId, brokerId);
        return this.fileService.listTransport(groupId, brokerId);
    }

    @PostMapping(path = "/closeTransport")
    @ResponseBody
    public GovernanceResult<Boolean> closeTransport(@RequestBody FileTransportChannelEntity fileTransport) throws GovernanceException {
        log.info("closeTransport, groupId:{}, brokerId:{}, transportId:{}, roleId:{}, topic:{}.", fileTransport.getGroupId(),
                fileTransport.getBrokerId(), fileTransport.getId(), fileTransport.getRole(), fileTransport.getTopicName());
        return fileService.closeTransport(fileTransport);
    }

    @GetMapping(path = "/genPemFile")
    public void genPemFile(@RequestParam(name = "groupId") String groupId,
                           @RequestParam(name = "brokerId") Integer brokerId,
                           HttpServletResponse response) throws GovernanceException {

        log.info("genPemFile, groupId:{}, brokerId:{}.", groupId, brokerId);
        List<FileTransportChannelEntity> list = this.fileService.listTransport(groupId, brokerId).getData();
        if (list.size() == 0) {
            throw new GovernanceException("please create file transport");
        }

        log.info("download file, groupId:{}, brokerId:{}.", groupId, brokerId);
        response.setHeader("content-type", "application/octet-stream");
        response.setContentType("application/octet-stream; charset=UTF-8");

        String downloadFile = this.fileService.genPemFile(groupId, brokerId);
        if (StringUtils.isBlank(downloadFile)) {
            throw new GovernanceException("download file not exist");
        }
        String fileName = downloadFile.split("/")[2];

        try {
            response.setHeader("filename", URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()));
        } catch (UnsupportedEncodingException e) {
            log.error("encode fileName error, fileName:{}", fileName, e);
        }

        byte[] buffer = new byte[1024];

        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(downloadFile));
             OutputStream os = response.getOutputStream()) {
            int i = bis.read(buffer);
            while (i != -1) {
                os.write(buffer, 0, i);
                os.flush();
                i = bis.read(buffer);
            }
            log.info("download file success, groupId:{}, fileName:{}", groupId, fileName);
        } catch (IOException e) {
            log.error("download file error, groupId:{} fileName:{}", groupId, fileName, e);
            throw new GovernanceException("download file error", e);
        }
    }

    @GetMapping(path = "/checkUploaded")
    @ResponseBody
    public GovernanceResult<Object> checkFileIsUploaded(@RequestParam(name = "groupId") String groupId,
                                                        @RequestParam(name = "brokerId") Integer brokerId,
                                                        @RequestParam(name = "topicName") String topicName,
                                                        @RequestParam(name = "fileName") String fileName,
                                                        @RequestParam(name = "nodeAddress") String nodeAddress,
                                                        @RequestParam(name = "role") String role) throws GovernanceException {
        log.info("checkFileIsUploaded, groupId:{}, topic:{}, fileName:{}, nodeAddress:{}, role:{}.", groupId, topicName, fileName, nodeAddress, role);
        return this.fileService.checkFileIsUploaded(groupId, brokerId, topicName, fileName, nodeAddress, role);
    }

}