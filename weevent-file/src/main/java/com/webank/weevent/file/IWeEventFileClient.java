package com.webank.weevent.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import com.webank.weevent.client.BrokerException;
import com.webank.weevent.client.SendResult;
import com.webank.weevent.core.config.FiscoConfig;
import com.webank.weevent.file.dto.FileChunksMetaPlus;
import com.webank.weevent.file.dto.FileTransportStats;
import com.webank.weevent.file.ftpclient.FtpInfo;
import com.webank.weevent.file.inner.DiskFiles;
import com.webank.weevent.file.service.FileChunksMeta;
import com.webank.weevent.file.service.WeEventFileClient;

public interface IWeEventFileClient {
    static IWeEventFileClient build(String groupId, String filePath, int fileChunkSize, FiscoConfig fiscoConfig) {
        return new WeEventFileClient(groupId, filePath, fileChunkSize, fiscoConfig);
    }

    static IWeEventFileClient build(String groupId, String filePath, FtpInfo ftpInfo, int fileChunkSize, FiscoConfig fiscoConfig) {
        return new WeEventFileClient(groupId, filePath, ftpInfo, fileChunkSize, fiscoConfig);
    }

    /**
     * open transport for sender.
     *
     * @param topic topic name
     */
    void openTransport4Sender(String topic);

    /**
     * open transport for authentication sender.
     *
     * @param topic topic name
     * @param publicPem public pem InputStream
     * @throws BrokerException broker exception
     */
    void openTransport4Sender(String topic, InputStream publicPem) throws BrokerException;

    /**
     * open transport for authentication sender.
     *
     * @param topic topic name
     * @param publicPem public pem path string
     * @throws BrokerException broker exception
     * @throws IOException IOException
     */
    void openTransport4Sender(String topic, String publicPem) throws BrokerException, IOException;

    /**
     * Publish a file to topic.
     * The file's data DO NOT stored in block chain. Yes, it's not persist, may be deleted sometime after subscribe notify.
     *
     * @param topic binding topic
     * @param localFile local file to be send
     * @param overwrite if receiver has this file, overwrite it?
     * @return send result, SendResult.SUCCESS if success, and return SendResult.eventId
     * @throws BrokerException broker exception
     * @throws IOException IOException
     */
    FileChunksMeta publishFile(String topic, String localFile, boolean overwrite) throws BrokerException, IOException;

    /**
     * open transport for receiver.
     *
     * @param topic topic name
     * @param fileListener notify interface
     * @throws BrokerException broker exception
     */
    void openTransport4Receiver(String topic, FileListener fileListener) throws BrokerException;
    
    /**
     * get Subscribers
     * @param topic topic name
     * @return String Set
     * @throws BrokerException broker exception
     */
    Set<String> getSubscribers(String topic) throws BrokerException;

    /**
     * open transport for authentication receiver.
     *
     * @param topic topic name
     * @param fileListener notify interface
     * @param privatePem private key pem InputStream
     * @throws BrokerException broker exception
     */
    void openTransport4Receiver(String topic, FileListener fileListener, InputStream privatePem) throws BrokerException;

    /**
     * open transport for authentication receiver.
     *
     * @param topic topic name
     * @param fileListener notify interface
     * @param privatePem private key pem path string
     * @throws IOException IOException
     * @throws BrokerException BrokerException
     */
    void openTransport4Receiver(String topic, FileListener fileListener, String privatePem) throws IOException, BrokerException;


    /**
     * Interface for file notify callback
     */
    interface FileListener {
        /**
         * Called while new file arrived.
         *
         * @param topicName topic name
         * @param fileName file name
         */
        void onFile(String topicName, String fileName);

        /**
         * Called while raise exception.
         *
         * @param e the e
         */
        void onException(Throwable e);
    }

    /**
     * close transport.
     *
     * @param topic topic name
     */
    void closeTransport(String topic);

    /**
     * query transport status.
     *
     * @param topic topic name
     * @return FileTransportStats
     */
    FileTransportStats status(String topic);

    /**
     * list received files.
     *
     * @param group group id
     * @param topic topic name
     * @return FileChunksMeta list
     * @throws BrokerException broker exception
     */
    List<FileChunksMeta> listFiles(String group, String topic) throws BrokerException;

    /**
     * sign a file transport event.
     *
     * @param fileChunksMeta fileChunksMeta
     * @return send result and eventId
     * @throws BrokerException broker exception
     */
    SendResult sign(FileChunksMeta fileChunksMeta) throws BrokerException;

    /**
     * verify a file transport event.
     *
     * @param eventId eventId return by sign
     * @param groupId group id
     * @return file and block information
     * @throws BrokerException broker exception
     */
    FileChunksMetaPlus verify(String eventId, String groupId) throws BrokerException;

    /**
     * get DiskFiles.
     *
     * @return DiskFiles
     */
    DiskFiles getDiskFiles();

    /**
     * generate pem key pair.
     * @return genPemFile url
     * @throws BrokerException BrokerException
     */
    String genPemFile() throws BrokerException;

    /**
     * Check if the receiver end has a file.
     *
     * @param fileName file name
     * @param topic topic name
     * @param groupId group id
     * @return is file exist
     * @throws BrokerException BrokerException
     */
    boolean isFileExist(String fileName, String topic, String groupId) throws BrokerException;
}
