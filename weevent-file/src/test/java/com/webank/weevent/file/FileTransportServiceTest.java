package com.webank.weevent.file;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.core.FiscoBcosInstance;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.fisco.util.WeEventUtils;
import com.webank.weevent.file.dto.FileEvent;
import com.webank.weevent.file.inner.AMOPChannel;
import com.webank.weevent.file.inner.DiskFiles;
import com.webank.weevent.file.inner.FileTransportService;
import com.webank.weevent.file.service.FileChunksMeta;

import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

@Slf4j
public class FileTransportServiceTest {
    private FileTransportService fileTransportService;
    private String localReceivePath = "./logs";
    private int fileChunkSize = 1024 * 1024;
    private String groupId = "1";
    private FileChunksMeta fileChunksMeta;


    @Before
    public void before() throws BrokerException {

        FiscoConfig fiscoConfig = new FiscoConfig();
        fiscoConfig.load("");
        // create fisco instance
        FiscoBcosInstance fiscoBcosInstance = new FiscoBcosInstance(fiscoConfig);

        // create producer
        IProducer iProducer = fiscoBcosInstance.buildProducer();
        iProducer.startProducer();

        // create consumer
        IConsumer iConsumer = fiscoBcosInstance.buildConsumer();
        iConsumer.startConsumer();

        // create FileTransportService instance
        FileTransportService fileTransportService = new FileTransportService(fiscoConfig, iProducer, "", this.localReceivePath, this.fileChunkSize, this.groupId);
        this.fileTransportService = fileTransportService;

        this.fileChunksMeta = new FileChunksMeta(WeEventUtils.generateUuid(),
                "abc.txt",
                100,
                "fce6f5f5d390fc1928c48eeb4e9271e9",
                "com.weevent.file",
                "1",
                true);
        this.fileChunksMeta.initChunkSize(32);
    }

    /**
     * Method: getChannel()
     */
    @Test
    public void testGetChannel() {
        AMOPChannel channel = this.fileTransportService.getChannel();
        Assert.assertNotNull(channel);
    }

    /**
     * Method: getFiscoConfig()
     */
    @Test
    public void testGetFiscoConfig() {
        FiscoConfig fiscoConfig = this.fileTransportService.getFiscoConfig();
        Assert.assertNotNull(fiscoConfig);
    }

    /**
     * Method: getDiskFiles()
     */
    @Test
    public void testGetDiskFiles() {
        DiskFiles diskFiles = this.fileTransportService.getDiskFiles();
        Assert.assertNotNull(diskFiles);
    }

    /**
     * Method: stats(boolean all, String groupId, String topicName)
     */
    @Test
    public void testStats() {
        this.fileTransportService.stats(true, this.fileChunksMeta.getGroupId(), this.fileChunksMeta.getTopic(), "127.0.0.1:20200");
        Assert.assertTrue(true);
    }

    /**
     * Method: sendSign(FileChunksMeta fileChunksMeta)
     */
    @Test
    @Ignore
    public void testSendSign() throws BrokerException {
        this.fileTransportService.sendSign(this.fileChunksMeta);
        Assert.assertNotNull(true);
    }

    /**
     * Method: prepareReceiveFile(FileChunksMeta fileChunksMeta)
     */
    @Test
    public void testPrepareReceiveFile() throws BrokerException {
        FileChunksMeta prepareFileRet = this.fileTransportService.prepareReceiveFile(this.fileChunksMeta);
        Assert.assertNotNull(prepareFileRet);
    }

    /**
     * Method: loadFileChunksMeta(String fileId)
     */
    @Test
    public void testLoadFileChunksMeta() throws BrokerException {
        FileChunksMeta prepareFileRet = this.fileTransportService.prepareReceiveFile(this.fileChunksMeta);
        Assert.assertNotNull(prepareFileRet);
        FileChunksMeta loadFileRet = this.fileTransportService.loadFileChunksMeta(fileChunksMeta.getFileId());
        Assert.assertNotNull(loadFileRet);
    }

    /**
     * Method: cleanUpReceivedFile(String fileId)
     */
    @Test
    public void testCleanUpReceivedFile() throws BrokerException {
        FileChunksMeta prepareFileRet = this.fileTransportService.prepareReceiveFile(this.fileChunksMeta);
        Assert.assertNotNull(prepareFileRet);
        FileChunksMeta cleanUpRet = this.fileTransportService.cleanUpReceivedFile(this.fileChunksMeta.getFileId());
        Assert.assertNotNull(cleanUpRet);
    }

    /**
     * Method: writeChunkData(FileEvent fileEvent)
     */
    @Test
    public void testWriteChunkData() throws BrokerException {
        FileChunksMeta prepareFileRet = this.fileTransportService.prepareReceiveFile(this.fileChunksMeta);
        Assert.assertNotNull(prepareFileRet);

        FileEvent fileEvent = new FileEvent(FileEvent.EventType.FileChannelStart, fileChunksMeta.getFileId());
        fileEvent.setFileChunksMeta(fileChunksMeta);
        fileEvent.setFileId(fileChunksMeta.getFileId());
        fileEvent.setChunkData("aaaaaaaaaa".getBytes());

        this.fileTransportService.writeChunkData(fileEvent);
        Assert.assertTrue(true);
    }
}
