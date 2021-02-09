package com.webank.weevent.file.service;


import java.io.IOException;
import java.util.BitSet;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

/**
 * File chunk information.
 * support file larger then 2G, be carefully fileSize
 *
 * @author matthewliu
 * @since 2020/02/12
 */
@Getter
public class FileChunksMeta {
    // uuid for file
    private String fileId;
    // file name in biz
    private String fileName;
    // file size in byte
    private long fileSize;
    // file data's md5
    private String fileMd5;
    // topic
    private String topic;
    // groupId
    private String groupId;
    // overwrite
    private boolean overwrite;
    // nodeAddress
    private String nodeAddress;

    // chunk size
    private int chunkSize = 0;
    // chunk num
    private int chunkNum = 0;
    // all chunk's upload status(chunkIndex, true/false), chunkIndex begin with 0
    @JsonSerialize(using = BitSetJsonSerializer.class)
    @JsonDeserialize(using = BitSetJsonDeserializer.class)
    private BitSet chunkStatus;

    // start time in unix timestamp
    private int startTime = 0;
    // file receiver's host
    private String host;

    public FileChunksMeta(String fileId,
                          String fileName,
                          long fileSize,
                          String fileMd5,
                          String topic,
                          String groupId,
                          boolean overwrite) {
        this.fileId = fileId;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.fileMd5 = fileMd5;
        this.topic = topic;
        this.groupId = groupId;
        this.overwrite = overwrite;

        this.startTime = (int) (System.currentTimeMillis() / 1000);
    }

    public void initChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
        this.chunkNum = (int) ((this.fileSize + this.chunkSize - 1) / this.chunkSize);
        this.chunkStatus = new BitSet(this.chunkNum);
    }

    public boolean checkChunkFull() {
        return this.chunkStatus.cardinality() == this.chunkNum;
    }

    public void cleanChunkStatus() {
        this.chunkStatus = null;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    public void setNodeAddress(String nodeAddress) {
        this.nodeAddress = nodeAddress;
    }
    
    public void clearPrivacy() {
        this.host = "";
        this.fileName = "";
        this.fileSize = 0;
    }

    // this is for jackson Serializer/Deserializer
    private FileChunksMeta() {
    }

    static class BitSetJsonSerializer extends JsonSerializer<BitSet> {
        @Override
        public void serialize(BitSet value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeBinary(value.toByteArray());
        }
    }

    static class BitSetJsonDeserializer extends JsonDeserializer<BitSet> {
        @Override
        public BitSet deserialize(JsonParser p, DeserializationContext deserializationContext) throws IOException {
            return BitSet.valueOf(p.getBinaryValue());
        }
    }
}
