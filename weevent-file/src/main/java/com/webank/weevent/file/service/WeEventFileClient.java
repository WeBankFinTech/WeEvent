package com.webank.weevent.file.service;

import java.io.File;
import java.io.FileInputStream;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.ErrorCode;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.core.FiscoBcosInstance;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.file.IWeEventFileClient;
import com.webank.weevent.file.dto.FileChunksMetaPlus;
import com.webank.weevent.file.dto.FileChunksMetaStatus;
import com.webank.weevent.file.dto.FileTransportStats;
import com.webank.weevent.file.ftpclient.FtpClientService;
import com.webank.weevent.file.ftpclient.FtpInfo;
import com.webank.weevent.file.inner.AMOPChannel;
import com.webank.weevent.file.inner.DiskFiles;
import com.webank.weevent.file.inner.FileTransportService;
import com.webank.weevent.file.inner.PemFile;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.fisco.bcos.sdk.amop.Amop;
import org.fisco.bcos.sdk.client.protocol.response.Peers.PeerInfo;
import org.fisco.bcos.sdk.crypto.keystore.KeyTool;
import org.fisco.bcos.sdk.crypto.keystore.PEMKeyStore;

@Slf4j
public class WeEventFileClient implements IWeEventFileClient {

    private static final String ZIP_NAME = "PPK.zip";
    private static final String FILE_PATH = "./logs";
    private static final String PATH_SEPARATOR = "/";
    private static final String PRIVATE_KEY_SUFFIX = ".pem";
    private static final String PUBLIC_KEY_SUFFIX = ".pub.pem";
    private static final String HEX_HEADER = "0x";
    private static final String PRIVATE_KEY_DESC = "PRIVATE KEY";
    private static final String PUBLIC_KEY_DESC = "PUBLIC KEY";
    private static final String ALGORITHM = "ECDSA";
    private static final String CURVE_TYPE = "SECP256k1";

    private final String groupId;
    private String localReceivePath = "";
    private FiscoConfig config;
    private FileTransportService fileTransportService;
    private int fileChunkSize;
    private FtpInfo ftpInfo;

    public WeEventFileClient(String groupId, String localReceivePath, int fileChunkSize, FiscoConfig fiscoConfig) {
        this.groupId = groupId;
        this.localReceivePath = localReceivePath;
        this.fileChunkSize = fileChunkSize;
        this.config = fiscoConfig;
        init();
    }

    public WeEventFileClient(String groupId, String localReceivePath, FtpInfo ftpInfo, int fileChunkSize, FiscoConfig fiscoConfig) {
        this.groupId = groupId;
        this.localReceivePath = localReceivePath;
        this.ftpInfo = ftpInfo;
        this.fileChunkSize = fileChunkSize;
        this.config = fiscoConfig;
        init();
    }

    public void init() {
        try {
            // create fisco instance
            FiscoBcosInstance fiscoBcosInstance = new FiscoBcosInstance(this.config);

            // create producer
            IProducer iProducer = fiscoBcosInstance.buildProducer();
            iProducer.startProducer();

            // create consumer
            IConsumer iConsumer = fiscoBcosInstance.buildConsumer();
            iConsumer.startConsumer();

            // create FileTransportService instance
            this.fileTransportService = new FileTransportService(this.config, iProducer, "", this.localReceivePath, this.fileChunkSize, this.groupId);
        } catch (BrokerException e) {
            log.error("init WeEventFileClient failed", e);
        }
    }

    public void openTransport4Sender(String topic) {
        if (!this.fileTransportService.getChannel().senderTopics.contains(topic)) {
            this.fileTransportService.getChannel().senderTopics.add(topic);
        }
    }

    public void openTransport4Sender(String topic, InputStream publicPem) throws BrokerException {
        // publicPem is public key
        // get AMOPChannel, fileTransportService and amopChannel is One-to-one correspondence
        AMOPChannel amopChannel = this.fileTransportService.getChannel();

        // service is exist
        if (amopChannel.getSenderTopics().contains(topic)) {
            log.error("this is already sender side for topic: {}", topic);
            throw new BrokerException(ErrorCode.FILE_SENDER_RECEIVER_CONFLICT);
        }

        // service not exist, new service
        Amop amop = amopChannel.amop;

        List<KeyTool> keyToolList = new ArrayList<>();
        try {
            keyToolList.add(new PEMKeyStore(publicPem));
        } catch (Exception e) {
            log.error("load public key in pem format failed.", e);
            throw new BrokerException(ErrorCode.FILE_PEM_KEY_INVALID);
        }

        amop.publishPrivateTopic(topic, keyToolList);
        this.fileTransportService.getChannel().topic2PublicKeys.put(topic, keyToolList);

        // put <topic-service> to map in AMOPChannel
        amopChannel.senderVerifyTopics.add(topic);
    }

    public void openTransport4Sender(String topic, String publicPemPath) throws BrokerException, IOException {
        if (StringUtils.isBlank(publicPemPath)) {
            log.error("public key pem path is blank.");
            throw new BrokerException(ErrorCode.PARAM_ISNULL);
        }
        File file = new File(publicPemPath);
        if (!file.isFile()) {
            log.error("public key file path string isn't a file.");
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        }
        InputStream inputStream = new FileInputStream(file);

        openTransport4Sender(topic, inputStream);
    }

    @Override
    public FileChunksMeta publishFile(String topic, String filePath, boolean overwrite, String nodeAddress, String role) throws BrokerException, IOException {
        // check if topic is exist
        IProducer iProducer = this.fileTransportService.getProducer();
        if (!iProducer.exist(topic, this.groupId)) {
            log.info("topic: " + topic + " not exist in group: " + groupId + ", open topic: " + topic + " groupID: " + groupId);
            boolean resOpen = iProducer.open(topic, this.groupId);
            if (!resOpen) {
                log.error("create topic: {} failed.", topic);
                throw new BrokerException(ErrorCode.TOPIC_CREATE_FAILED);
            }
        }

        String newTopic = this.fileTransportService.getChannel().switchTopic(topic, nodeAddress, role);
        boolean resOpen = iProducer.open(newTopic, this.groupId);
        if (!resOpen) {
            log.error("create topic: {} failed.", newTopic);
            throw new BrokerException(ErrorCode.TOPIC_CREATE_FAILED);
        }

        AMOPChannel amopChannel = this.fileTransportService.getChannel();
        if (amopChannel.senderTopics.contains(topic)) {
            this.fileTransportService.getChannel().senderTopics.add(newTopic);
        } else if (amopChannel.senderVerifyTopics.contains(topic)) {
            Amop amop = amopChannel.amop;
            List<KeyTool> keyToolList = this.fileTransportService.getChannel().topic2PublicKeys.get(topic);
            if (keyToolList.size() == 0) {
                log.error("no public key corresponding to topic: {}.", topic);
                throw new BrokerException("unable to get public key after switching topics.");
            }

            amop.publishPrivateTopic(newTopic, keyToolList);
            amopChannel.senderVerifyTopics.add(newTopic);
        } else {
            log.error("not a sender topic: {}.", topic);
            throw new BrokerException("not a sender topic.");
        }

        FileChunksMeta fileChunksMeta;
        if (this.ftpInfo == null) {
            // publish local file
            validateLocalFile(filePath);

            FileChunksTransport fileChunksTransport = new FileChunksTransport(this.fileTransportService);
            fileChunksMeta = fileChunksTransport.upload(filePath, newTopic, this.groupId, overwrite, nodeAddress, role);
        } else {
            // publish ftp file
            FtpClientService ftpClientService = new FtpClientService();
            ftpClientService.connect(this.ftpInfo.getHost(), this.ftpInfo.getPort(), this.ftpInfo.getUserName(), this.ftpInfo.getPassWord());
            ftpClientService.downLoadFile(filePath, this.localReceivePath);

            FileChunksTransport fileChunksTransport = new FileChunksTransport(this.fileTransportService);
            fileChunksMeta = fileChunksTransport.upload(this.localReceivePath + filePath.substring(filePath.indexOf('/')), newTopic, this.groupId, overwrite, nodeAddress, role);
        }
        return fileChunksMeta;
    }

    public void openTransport4Receiver(String topic, FileListener fileListener) throws BrokerException {
        AMOPChannel amopChannel = this.fileTransportService.getChannel();

        FileEventListener fileEventListener = new FileEventListener(this.localReceivePath, this.ftpInfo, fileListener);

        amopChannel.subTopic(topic, fileEventListener);
    }
    
    public Set<String> getSubscribers(String topic) throws BrokerException {
        AMOPChannel amopChannel = this.fileTransportService.getChannel();
        return amopChannel.getSubscribers(topic);
    }

    public void openTransport4Receiver(String topic, FileListener fileListener, InputStream privatePem) throws BrokerException {
        // get AMOPChannel, fileTransportService and amopChannel is One-to-one correspondence
        AMOPChannel amopChannel = this.fileTransportService.getChannel();

        FileEventListener fileEventListener = new FileEventListener(this.localReceivePath, this.ftpInfo, fileListener);

        amopChannel.subTopic(topic, privatePem, fileEventListener);
    }

    public void openTransport4Receiver(String topic, FileListener fileListener, String privatePemPath) throws IOException, BrokerException {
        if (StringUtils.isBlank(privatePemPath)) {
            log.error("private key pem path is blank.");
            throw new BrokerException(ErrorCode.PARAM_ISNULL);
        }
        File file = new File(privatePemPath);
        if (!file.isFile()) {
            log.error("private key file path string isn't a file.");
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        }
        InputStream inputStream = new FileInputStream(file);

        openTransport4Receiver(topic, fileListener, inputStream);
    }

    public void closeTransport(String topic) {
        AMOPChannel channel = this.fileTransportService.getChannel();
        // unSubscribe topic
        if (!channel.getSubTopics().isEmpty()) {
            channel.unSubTopic(topic);
        } else {
            if (!channel.getSenderTopics().isEmpty()) {
                // delete transport
                channel.deleteTransport(topic);
            }
        }
    }

    @Override
    public FileTransportStats status(String topicName, String nodeAddress) {
        FileTransportStats fileTransportStats = this.fileTransportService.stats(true, this.groupId, topicName, nodeAddress);
        if (fileTransportStats == null) {
            log.error("get status error");
            return null;
        }

        // sender
        Map<String, List<FileChunksMetaStatus>> senderTopicStatusMap = new HashMap<>();
        List<FileChunksMetaStatus> senderFileChunksMetaStatusList = fileTransportStats.getSender().get(groupId).get(topicName);
        senderTopicStatusMap.put(topicName, senderFileChunksMetaStatusList);
        Map<String, Map<String, List<FileChunksMetaStatus>>> sender = new HashMap<>();
        sender.put(groupId, senderTopicStatusMap);

        // receiver
        Map<String, List<FileChunksMetaStatus>> receiverTopicStatusMap = new HashMap<>();
        List<FileChunksMetaStatus> receiverFileChunksMetaStatusList = fileTransportStats.getReceiver().get(groupId).get(topicName);
        receiverTopicStatusMap.put(topicName, receiverFileChunksMetaStatusList);
        Map<String, Map<String, List<FileChunksMetaStatus>>> receiver = new HashMap<>();
        receiver.put(groupId, receiverTopicStatusMap);

        FileTransportStats retFileTransportStats = new FileTransportStats();
        retFileTransportStats.setSender(sender);
        retFileTransportStats.setReceiver(receiver);

        return retFileTransportStats;
    }

    public List<FileChunksMeta> listFiles(String group, String topic, String nodeAddress) throws BrokerException {
    	nodeAddress = nodeAddress.replace(",", "").replace(":", "").replace(".", "");
        // get json from disk
        List<File> fileList = new ArrayList<>();
        String filePath = this.localReceivePath + PATH_SEPARATOR + group + PATH_SEPARATOR + nodeAddress + PATH_SEPARATOR + topic;
        if (filePath.indexOf("..") != -1) {
            log.info("file path not exist.. filePath, {}", filePath);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        }
        File file = new File(filePath);
        if (!file.exists()) {
            file.mkdirs();
        }
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".json")) {
                    fileList.add(f);
                }
            }
        } else {
            log.error("the directory is empty, {}.", filePath);
            throw new BrokerException(ErrorCode.FILE_NOT_EXIST);
        }

        List<FileChunksMeta> fileChunksMetaList = new ArrayList<>();
        DiskFiles diskFiles = new DiskFiles(filePath);
        for (File f : fileList) {
            FileChunksMeta fileChunksMeta = diskFiles.loadFileMeta(f);
            if (topic.equals(fileChunksMeta.getTopic()) && fileChunksMeta.checkChunkFull()) {
                fileChunksMetaList.add(fileChunksMeta);
            }
        }

        return fileChunksMetaList;
    }

    public SendResult sign(FileChunksMeta fileChunksMeta) throws BrokerException {
        return this.fileTransportService.sendSign(fileChunksMeta);
    }

    public FileChunksMetaPlus verify(String eventId, String groupId) throws BrokerException {
        return this.fileTransportService.verify(eventId, groupId);

    }

    public DiskFiles getDiskFiles() {
        return this.fileTransportService.getDiskFiles();
    }

    public String genPemFile() throws BrokerException {
        try {
            BouncyCastleProvider prov = new BouncyCastleProvider();
            Security.addProvider(prov);

            ECNamedCurveParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(CURVE_TYPE);
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM, prov.getName());
            generator.initialize(ecSpec, new SecureRandom());
            KeyPair pair = generator.generateKeyPair();
            String pubKey = pair.getPublic().toString();
            String account = HEX_HEADER + pubKey.substring(pubKey.indexOf("[") + 1, pubKey.indexOf("]")).replace(":", "");

            PemFile privatePemFile = new PemFile(pair.getPrivate(), PRIVATE_KEY_DESC);
            PemFile publicPemFile = new PemFile(pair.getPublic(), PUBLIC_KEY_DESC);

            String privateKeyUrl = FILE_PATH + PATH_SEPARATOR + account + PRIVATE_KEY_SUFFIX;
            String publicKeyUrl = FILE_PATH + PATH_SEPARATOR + account + PUBLIC_KEY_SUFFIX;

            privatePemFile.write(privateKeyUrl);
            publicPemFile.write(publicKeyUrl);

            File[] srcFiles = {new File(privateKeyUrl), new File(publicKeyUrl)};
            String zipUrl = FILE_PATH + "/" + ZIP_NAME;
            File zipFile = new File(zipUrl);

            zipFiles(srcFiles, zipFile);
            for (File file : srcFiles) {
                file.delete();
            }
            return zipUrl;
        } catch (IOException | NoSuchProviderException | NoSuchAlgorithmException
                | InvalidAlgorithmParameterException e) {
            log.error("generate pem file error", e);
            throw new BrokerException(ErrorCode.FILE_GEN_PEM_BC_FAILED);
        }
    }

    private static void zipFiles(File[] srcFiles, File zipFile) throws IOException {
        byte[] buf = new byte[1024];
        if (!zipFile.exists()) {
            zipFile.createNewFile();
        }
        try (FileOutputStream fileOutPutStream = new FileOutputStream(zipFile); ZipOutputStream zipOutPutStream = new ZipOutputStream(fileOutPutStream)) {
            for (int i = 0; i < srcFiles.length; i++) {
                try (FileInputStream fileInputStream = new FileInputStream(srcFiles[i])) {
                    zipOutPutStream.putNextEntry(new ZipEntry(srcFiles[i].getName()));
                    int len;
                    while ((len = fileInputStream.read(buf)) > 0) {
                        zipOutPutStream.write(buf, 0, len);
                    }
                    zipOutPutStream.closeEntry();
                }
            }
        }
    }

    public boolean isFileExist(String fileName, String topic, String groupId, String nodeAddress, String role) throws BrokerException {
        return this.fileTransportService.getFileExistence(fileName, topic, groupId, nodeAddress, role);
    }

    private static void validateLocalFile(String filePath) throws BrokerException {
        if (StringUtils.isBlank(filePath)) {
            throw new BrokerException(ErrorCode.LOCAL_FILE_IS_EMPTY);
        }
        if (!(new File(filePath)).exists()) {
            throw new BrokerException(ErrorCode.LOCAL_FILE_NOT_EXIST);
        }
    }

    /**
     * Interface for event notify callback
     */
    public interface EventListener {
        /**
         * check if file exists at ftp server.
         *
         * @param fileName file name
         * @return true if file exist.
         */
        boolean checkFile(String fileName);

        /**
         * Called while new event arrived.
         *
         * @param topic topic name
         * @param fileName file name
         */
        void onEvent(String topic, String fileName);

        /**
         * Called while raise exception.
         *
         * @param e the e
         */
        void onException(Throwable e);
    }

    static class FileEventListener implements EventListener {
        private final String receivePath;
        private final FtpInfo ftpInfo;
        private final FileListener fileListener;

        public FileEventListener(String receivePath, FtpInfo ftpInfo, FileListener fileListener) {
            this.receivePath = receivePath;
            this.ftpInfo = ftpInfo;
            this.fileListener = fileListener;
        }

        public boolean checkFile(String fileName) {
            boolean ret = false;
            if (this.ftpInfo != null) {
                try {
                    FtpClientService ftpClientService = new FtpClientService();
                    ftpClientService.connect(this.ftpInfo.getHost(), this.ftpInfo.getPort(), this.ftpInfo.getUserName(),
                            this.ftpInfo.getPassWord());

                    // check file exist
                    if (StringUtils.isBlank(this.ftpInfo.getFtpReceivePath())) {
                        List<String> files = ftpClientService.getFileList("./");
                        for (String file : files) {
                            if (file.equals(fileName)) {
                                ret = true;
                                break;
                            }
                        }
                    } else {
                        List<String> files = ftpClientService.getFileList(this.ftpInfo.getFtpReceivePath());
                        for (String file : files) {
                            if (file.equals(fileName)) {
                                ret = true;
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return ret;
        }

        @Override
        public void onEvent(String topic, String fileName) {
            // upload file to ftp
            if (this.ftpInfo != null) {
                try {
                    FtpClientService ftpClientService = new FtpClientService();
                    ftpClientService.connect(this.ftpInfo.getHost(), this.ftpInfo.getPort(), this.ftpInfo.getUserName(),
                            this.ftpInfo.getPassWord());
                    // upload file
                    if (StringUtils.isBlank(this.ftpInfo.getFtpReceivePath())) {
                        log.info("upload file to ftp server, file：{}", fileName);
                        ftpClientService
                                .upLoadFile(this.receivePath + PATH_SEPARATOR + topic + PATH_SEPARATOR + fileName);
                    } else {
                        // specify upload directory
                        log.info("upload file to ftp server, to path: {}, file：{}", this.ftpInfo.getFtpReceivePath(),
                                fileName);
                        ftpClientService.upLoadFile(this.ftpInfo.getFtpReceivePath(),
                                this.receivePath + PATH_SEPARATOR + topic + PATH_SEPARATOR + fileName);
                    }
                } catch (BrokerException e) {
                    e.printStackTrace();
                }
            }
            fileListener.onFile(topic, fileName);
        }

        public void onException(Throwable e) {
            this.fileListener.onException(e);
        }
    }
}
