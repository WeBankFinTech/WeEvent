package com.webank.weevent.file;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.WeEvent;
import com.webank.weevent.core.FiscoBcosInstance;
import com.webank.weevent.core.IConsumer;
import com.webank.weevent.core.IProducer;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.core.fisco.util.WeEventUtils;
import com.webank.weevent.file.inner.FileTransportService;
import com.webank.weevent.file.service.FileChunksMeta;
import com.webank.weevent.file.service.FileChunksTransport;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * FileChunks Tester.
 *
 * @author <matthewliu>
 * @version 1.0
 * @since <pre>02/12/2020</pre>
 */
public class FileChunksTransportTest {
    private FiscoConfig fiscoConfig;
    private FileTransportService fileTransportService;

    private String topicName = "com.weevent.file";
    private String groupId = "1";
    private String localReceivePath = "./logs";
    // chunk size 1MB
    private int fileChunkSize = 1048576;

    @Before
    public void before() throws BrokerException {
        this.fiscoConfig = new FiscoConfig();
        this.fiscoConfig.load("");
        FiscoBcosInstance fiscoBcosInstance = new FiscoBcosInstance(this.fiscoConfig);

        // create producer
        IProducer iProducer = fiscoBcosInstance.buildProducer();
        iProducer.startProducer();

        // create consumer
        IConsumer iConsumer = fiscoBcosInstance.buildConsumer();
        iConsumer.startConsumer();

        // create FileTransportService instance
        FileTransportService fileTransportService = new FileTransportService(this.fiscoConfig, iProducer, "", this.localReceivePath, this.fileChunkSize, this.groupId);
        this.fileTransportService = fileTransportService;
    }

    /**
     * Method: upload(String localFile)
     */
    @Test
    @Ignore
    public void testUpload() throws Exception {
        FileChunksTransport fileChunksTransport = new FileChunksTransport(this.fileTransportService);
        FileChunksMeta fileChunksMeta = fileChunksTransport.upload("src/main/resources/log4j2.xml", "com.weevent.file", WeEvent.DEFAULT_GROUP_ID, true, "127.0.0.1:20200", "1");
        Assert.assertNotNull(fileChunksMeta);
    }

    /**
     * Method: download(String host, String fileId)
     */
    @Test
    public void testDownload() {
        try {
            FileTransportService fileTransportService1 = mock(FileTransportService.class);

            FileChunksMeta fileChunksMeta = null;

            String fileMd5 = "b0497e189ea2bb2a06e7ce54ffbf351c";
            int fileSize = 1087;
            fileChunksMeta = new FileChunksMeta(WeEventUtils.generateUuid(),
                    URLDecoder.decode("log4j2.xml", StandardCharsets.UTF_8.toString()),
                    fileSize,
                    fileMd5,
                    this.topicName,
                    this.groupId, true);

            fileChunksMeta.initChunkSize(512);
            for (int i = 0; i < 3; i++) {
                fileChunksMeta.getChunkStatus().set(i);
            }

            when(fileTransportService1.openChannel(Mockito.any())).thenReturn(fileChunksMeta);
            when(fileTransportService1.getReceiverFileChunksMeta(Mockito.anyString(), Mockito.anyString(), Mockito.anyString())).thenReturn(fileChunksMeta);
            when(fileTransportService1.closeChannel(Mockito.anyString(), Mockito.anyString())).thenReturn(fileChunksMeta);

            FileChunksTransport fileChunksTransport = new FileChunksTransport(fileTransportService1);

            FileChunksMeta fileChunksMeta1 = fileChunksTransport.upload("src/main/resources/log4j2.xml", "com.weevent.file", WeEvent.DEFAULT_GROUP_ID, true, "127.0.0.1:20200", "1");
            Assert.assertNotNull(fileChunksMeta1);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
} 
