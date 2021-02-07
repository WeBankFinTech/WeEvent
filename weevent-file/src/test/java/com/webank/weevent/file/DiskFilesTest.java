package com.webank.weevent.file;

import java.io.File;
import java.util.List;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.core.fisco.util.WeEventUtils;
import com.webank.weevent.file.inner.DiskFiles;
import com.webank.weevent.file.service.FileChunksMeta;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * DiskFiles Tester.
 *
 * @author <matthewliu>
 * @version 1.0
 * @since <pre>02/18/2020</pre>
 */
public class DiskFilesTest {
    private DiskFiles diskFiles;
    private FileChunksMeta fileChunksMeta;

    @Before
    public void before() throws Exception {
        this.diskFiles = new DiskFiles("./logs/file");

        this.fileChunksMeta = new FileChunksMeta(WeEventUtils.generateUuid(),
                "abc.txt",
                100,
                "fce6f5f5d390fc1928c48eeb4e9271e9",
                "com.weevent.file",
                "1",
                true);
        this.fileChunksMeta.initChunkSize(32);
    }

    @After
    public void after() throws Exception {
        File file = new File("./logs/file");
        for (File f : file.listFiles()) {
            boolean ret = f.delete();
            if (!ret) {
                System.out.println("delete file error, file name: " + f.getName());
            }
        }
        boolean deleteRet = file.delete();
        if (!deleteRet) {
            System.out.println("delete file error, file name: " + file.getName());
        }

    }

    /**
     * Method: loadFileMeta(String fileId)
     */
    @Test
    public void testLoadFileMeta1() throws Exception {
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);
        this.diskFiles.saveFileMeta(this.fileChunksMeta);
        Assert.assertTrue(true);

        FileChunksMeta fileChunksMeta = this.diskFiles.loadFileMeta(this.fileChunksMeta.getFileId());
        Assert.assertEquals(this.fileChunksMeta.getFileId(), fileChunksMeta.getFileId());
        Assert.assertEquals(this.fileChunksMeta.getChunkNum(), fileChunksMeta.getChunkNum());
        Assert.assertTrue(true);
    }

    /**
     * Method: loadFileMeta(File fileMeta)
     */
    @Test
    public void testLoadFileMeta2() throws Exception {
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);
        this.diskFiles.saveFileMeta(this.fileChunksMeta);
        Assert.assertTrue(true);

        String fileName = this.fileChunksMeta.getFileName();
        String filePath = "./logs/file" + "/" + this.fileChunksMeta.getTopic() + "/";
        File fileMeta = new File(filePath + fileName + this.diskFiles.MetaFileSuffix);

        FileChunksMeta fileChunksMeta = this.diskFiles.loadFileMeta(fileMeta);
        Assert.assertEquals(this.fileChunksMeta.getFileId(), fileChunksMeta.getFileId());
        Assert.assertEquals(this.fileChunksMeta.getChunkNum(), fileChunksMeta.getChunkNum());
        Assert.assertTrue(true);
    }

    /**
     * Method: saveFileMeta(FileChunksMeta fileChunksMeta)
     */
    @Test
    public void testSaveFileMeta() throws Exception {
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);
        this.diskFiles.saveFileMeta(this.fileChunksMeta);
        Assert.assertTrue(true);
    }

    /**
     * Method: createFixedLengthFile(String fileId, long len)
     */
    @Test
    public void testCreateFixedLengthFile() throws Exception {
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);
        Assert.assertTrue(true);
    }

    /**
     * Method: writeChunkData(String fileId, int chunkIndex, byte[] chunkData)
     */
    @Test
    public void testWriteChunkData() throws Exception {
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);
        this.diskFiles.saveFileMeta(this.fileChunksMeta);
        this.diskFiles.writeChunkData(this.fileChunksMeta.getFileId(), 0, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
        Assert.assertTrue(true);
    }

    /**
     * Method: readChunkData(String fileId, int chunkIndex)
     */
    @Test
    public void testReadChunkData() throws Exception {
        byte[] chunkData = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);
        this.diskFiles.saveFileMeta(this.fileChunksMeta);
        this.diskFiles.writeChunkData(this.fileChunksMeta.getFileId(), 1, chunkData);
        byte[] data = this.diskFiles.readChunkData(this.fileChunksMeta.getFileId(), 1);

        Assert.assertArrayEquals(data, chunkData);
    }

    /**
     * Method: cleanUp(String fileId)
     */
    @Test
    public void testCleanUp() throws Exception {
        DiskFiles diskFile = new DiskFiles("./logs/file");

        FileChunksMeta fileChunksMeta = new FileChunksMeta(WeEventUtils.generateUuid(),
                "abc.txt",
                100,
                "fce6f5f5d390fc1928c48eeb4e9271e9",
                "com.weevent.file",
                "1",
                true);
        fileChunksMeta.initChunkSize(32);

        diskFile.createFixedLengthFile(fileChunksMeta);
        diskFile.saveFileMeta(fileChunksMeta);
        Assert.assertTrue(true);

        String fileName = fileChunksMeta.getFileName();
        String filePath = "./logs/file" + "/" + fileChunksMeta.getTopic() + "/";
        File fileMeta = new File(filePath + fileName + diskFile.MetaFileSuffix);
        Assert.assertTrue(fileMeta.exists());


        diskFile.cleanUp(fileChunksMeta.getFileId());
        Assert.assertTrue(!fileMeta.exists());

    }

    /**
     * Method: listNotCompleteFiles(boolean all, String topicName)
     */
    @Test
    @Ignore
    public void testListNotCompleteFiles() throws BrokerException {
        this.diskFiles.createFixedLengthFile(this.fileChunksMeta);

        List<FileChunksMeta> fileChunksMetaList = this.diskFiles.listNotCompleteFiles(true, "1", "com.weevent.file", "127.0.0.1:20200");
        Assert.assertTrue(!fileChunksMetaList.isEmpty());
    }
}
