package com.webank.weevent.file;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.file.dto.FileChunksMetaPlus;
import com.webank.weevent.file.dto.FileChunksMetaStatus;
import com.webank.weevent.file.dto.FileTransportStats;
import com.webank.weevent.file.ftpclient.FtpInfo;
import com.webank.weevent.file.inner.DiskFiles;
import com.webank.weevent.file.service.FileChunksMeta;
import com.webank.weevent.file.service.WeEventFileClient;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Slf4j
public class WeEventFileClientTest {

    private String topicName = "com.weevent.file";
    private String groupId = "1";
    private String localReceivePath = "./logs";
    // chunk size 1MB
    private int fileChunkSize = 1048576;
    private FiscoConfig fiscoConfig;

    private String host = "127.0.0.1";
    private int port = 21;
    private String userName = "ftpuser";
    private String passWd = "";


    @Before
    public void before() {
        this.fiscoConfig = new FiscoConfig();
        this.fiscoConfig.load("");
    }


    @Test
    @Ignore
    public void testPublishFile() throws Exception {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);

        weEventFileClient.openTransport4Sender(topicName);
        FileChunksMeta fileChunksMeta = weEventFileClient.publishFile(this.topicName,
                new File("src/main/resources/ca.crt").getAbsolutePath(), true, "127.0.0.1:20200", "1");
        Assert.assertNotNull(fileChunksMeta);
    }

    @Test
    public void testSubscribeFile() throws Exception {
        IWeEventFileClient.FileListener fileListener = new IWeEventFileClient.FileListener() {
            @Override
            public void onFile(String topicName, String fileName) {
                log.info("+++++++topic name: {}, file name: {}", topicName, fileName);
                System.out.println(new File(localReceivePath + "/" + fileName).getPath());
            }

            @Override
            public void onException(Throwable e) {
                e.printStackTrace();
            }
        };

        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        weEventFileClient.openTransport4Receiver(this.topicName, fileListener);

        Thread.sleep(1000);
        Assert.assertTrue(true);
    }

    @Test
    @Ignore
    public void testPublishFileWithVerify() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:" + "0x2809a9902e47d6fcaabe6d0183855d9201c93af1.public.pem");

        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);

        weEventFileClient.openTransport4Sender(this.topicName, resource.getInputStream());

        // handshake time delay for web3sdk
        Thread.sleep(1000 * 10L);

        FileChunksMeta fileChunksMeta = weEventFileClient.publishFile(this.topicName,
                new File("src/main/resources/ca.crt").getAbsolutePath(), true, "127.0.0.1:20200", "1");

        Assert.assertNotNull(fileChunksMeta);
    }

    @Test
    public void testSubscribeFileWithVerify() throws Exception {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:" + "0x2809a9902e47d6fcaabe6d0183855d9201c93af1.pem");

        IWeEventFileClient.FileListener fileListener = new IWeEventFileClient.FileListener() {
            @Override
            public void onFile(String topicName, String fileName) {
                log.info("+++++++topic name: {}, file name: {}", topicName, fileName);
                System.out.println(new File(localReceivePath + "/" + fileName).getPath());
            }

            @Override
            public void onException(Throwable e) {
                e.printStackTrace();
            }
        };

        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        weEventFileClient.openTransport4Receiver(this.topicName, fileListener, resource.getInputStream());

        Thread.sleep(1000);
        Assert.assertTrue(true);
    }

    @Test
    public void testCloseTransport() {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        weEventFileClient.closeTransport(this.topicName);
        Assert.assertTrue(true);
    }

    @Test
    public void testListFile() {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        try {
            List<FileChunksMeta> fileChunksMetas = weEventFileClient.listFiles(this.groupId, this.topicName, "127.0.0.1:20200");
            if (fileChunksMetas != null && fileChunksMetas.size()!=0) {
                for (FileChunksMeta fileChunksMeta : fileChunksMetas) {
                    System.out.println(fileChunksMeta.getFileName() + "  "+ fileChunksMeta.getTopic() + "  " + fileChunksMeta.getGroupId());
                }
            }
        } catch (BrokerException e) {
            e.printStackTrace();
        }
        Assert.assertTrue(true);
    }


    // new class for test status interface
    static class Runner4PublishFile implements Runnable {
        private final String topic;
        private final WeEventFileClient weEventFileClient;

        Runner4PublishFile(WeEventFileClient weEventFileClient, String topic) {
            this.weEventFileClient = weEventFileClient;
            this.topic = topic;
        }

        @Override
        public void run() {
            //publish file
            weEventFileClient.openTransport4Sender(this.topic);
            try {
                weEventFileClient.publishFile(topic,
                        new File("src/main/resources/bigfile.zip").getAbsolutePath(), true, "127.0.0.1:20200", "1");
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    static class Runner4SubscribeFile implements Runnable {
        private final String topic;
        private final WeEventFileClient weEventFileClient;

        Runner4SubscribeFile(WeEventFileClient weEventFileClient, String topic) {
            this.weEventFileClient = weEventFileClient;
            this.topic = topic;
        }

        @Override
        public void run() {
            //subscribe file
            IWeEventFileClient.FileListener fileListener = new IWeEventFileClient.FileListener() {
                @Override
                public void onFile(String topicName, String fileName) {
                    log.info("+++++++topic name: {}, file name: {}", topicName, fileName);
                }

                @Override
                public void onException(Throwable e) {
                    e.printStackTrace();
                }
            };
            try {
                weEventFileClient.openTransport4Receiver(topic, fileListener);
                Thread.sleep(1000 * 60 * 5L);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    static class Runner4Status implements Runnable {
        private final String groupId;
        private final String topic;
        private final WeEventFileClient weEventFileClient;
        private final boolean isSender;

        Runner4Status(String groupId, String topic, WeEventFileClient weEventFileClient, boolean isSender) {
            this.groupId = groupId;
            this.topic = topic;
            this.weEventFileClient = weEventFileClient;
            this.isSender = isSender;
        }

        @Override
        public void run() {
            FileTransportStats fileTransportStats = weEventFileClient.status(topic, "127.0.0.1:20200");
            List<FileChunksMetaStatus> fileChunksMetaStatusList = new ArrayList<>();
            if (isSender) {
                fileChunksMetaStatusList = fileTransportStats.getSender().get(groupId).get(topic);
                if (fileChunksMetaStatusList.size() != 0) {
                    System.out.println("sender speed:" + fileChunksMetaStatusList.get(0).getSpeed() + "   "
                            + "send chunk:" + fileChunksMetaStatusList.get(0).getReadyChunk() + "   "
                            + "send time cost:" + fileChunksMetaStatusList.get(0).getTime());
                } else {
                    System.out.println("get status failed, fileChunksMetaStatusList size = 0");
                }
            } else {
                fileChunksMetaStatusList = fileTransportStats.getReceiver().get(groupId).get(topic);
                if (fileChunksMetaStatusList.size() != 0) {
                    System.out.println("receiver speed:" + fileChunksMetaStatusList.get(0).getSpeed() + "   "
                            + "receive chunk:" + fileChunksMetaStatusList.get(0).getReadyChunk() + "   "
                            + "receive time cost:" + fileChunksMetaStatusList.get(0).getTime());
                } else {
                    System.out.println("get status failed, fileChunksMetaStatusList size = 0");
                }
            }

            Assert.assertNotNull(fileChunksMetaStatusList);
        }
    }

    @Test
    @Ignore
    public void testStatus4Sender() throws InterruptedException {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        new Thread(new Runner4PublishFile(weEventFileClient, this.topicName), "thread publish").start();
        // thread delay for get sender status
        System.out.println("sender delay 10s:");
        Thread.sleep(1000 * 10L);

        System.out.println("begin get sender status:");
        new Thread(new Runner4Status(this.groupId, this.topicName, weEventFileClient, true), "thread status").start();
        Thread.sleep(1000 * 60 * 5L);
        Assert.assertTrue(true);
    }

    @Test
    @Ignore
    public void testStatus4Receiver() throws InterruptedException {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        new Thread(new Runner4SubscribeFile(weEventFileClient, this.topicName), "thread publish").start();
        // thread delay for get receiver status
        System.out.println("receiver waiting sender publish file, delay 30s:");
        Thread.sleep(1000 * 30L);

        System.out.println("begin get receiver status: ");
        new Thread(new Runner4Status(this.groupId, this.topicName, weEventFileClient, false), "thread status").start();

        // main thread sleep, waiting for subscribe file
        Thread.sleep(1000 * 60 * 5L);
        Assert.assertTrue(true);
    }


    @Test
    @Ignore
    public void testSign() throws Exception {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);

        weEventFileClient.openTransport4Sender(topicName);
        FileChunksMeta fileChunksMeta = weEventFileClient.publishFile(this.topicName,
                new File("src/main/resources/ca.crt").getAbsolutePath(), true, "127.0.0.1:20200", "1");
        Assert.assertNotNull(fileChunksMeta);

        SendResult sendResult = weEventFileClient.sign(fileChunksMeta);
        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);
    }

    @Test
    @Ignore
    public void testVerify() throws Exception {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);

        // publish file
        weEventFileClient.openTransport4Sender(topicName);
        FileChunksMeta fileChunksMeta = weEventFileClient.publishFile(this.topicName,
                new File("src/main/resources/ca.crt").getAbsolutePath(), true, "127.0.0.1:20200", "1");
        Assert.assertNotNull(fileChunksMeta);

        // sign
        SendResult sendResult = weEventFileClient.sign(fileChunksMeta);
        Assert.assertEquals(sendResult.getStatus(), SendResult.SendResultStatus.SUCCESS);

        // verify
        FileChunksMetaPlus fileChunksMetaPlus = weEventFileClient.verify(sendResult.getEventId(), this.groupId);
        Assert.assertNotNull(fileChunksMetaPlus);
    }

    @Test
    public void testGetDiskFiles() {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        DiskFiles diskFiles = weEventFileClient.getDiskFiles();
        Assert.assertNotNull(diskFiles);
    }

    @Test
    @Ignore
    public void testPublishFileFromFtp() throws Exception {
        FtpInfo ftpInfo = new FtpInfo(this.host, this.port, this.userName, this.passWd, "");
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, ftpInfo, this.fileChunkSize, this.fiscoConfig);

        weEventFileClient.openTransport4Sender(topicName);
        FileChunksMeta fileChunksMeta = weEventFileClient.publishFile(this.topicName, "./test/build_chain.sh", true, "127.0.0.1:20200", "1");
        Assert.assertNotNull(fileChunksMeta);
    }

    @Test
    @Ignore
    public void testSubscribeFile2Ftp() throws Exception {
        IWeEventFileClient.FileListener fileListener = new IWeEventFileClient.FileListener() {
            @Override
            public void onFile(String topicName, String fileName) {
                log.info("+++++++topic name: {}, file name: {}", topicName, fileName);
                System.out.println(new File(localReceivePath + "/" + topicName + "/" + fileName).getPath());
            }

            @Override
            public void onException(Throwable e) {
                e.printStackTrace();
            }
        };

        FtpInfo ftpInfo = new FtpInfo(this.host, this.port, this.userName, this.passWd, "./2020052811");
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, ftpInfo, this.fileChunkSize, this.fiscoConfig);
        weEventFileClient.openTransport4Receiver(this.topicName, fileListener);

        Thread.sleep(1000 * 60 * 5L);
        Assert.assertTrue(true);
    }

    @Test
    public void testGetPemFile() throws BrokerException {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        weEventFileClient.genPemFile();
        Assert.assertTrue(true);
    }

    @Test
    @Ignore
    public void testIsFileExist() throws BrokerException {
        WeEventFileClient weEventFileClient = new WeEventFileClient(this.groupId, this.localReceivePath, this.fileChunkSize, this.fiscoConfig);
        boolean ret = weEventFileClient.isFileExist("ca.crt", this.topicName, this.groupId, "127.0.0.1:20200", "1");
        Assert.assertTrue(ret);
    }
}
