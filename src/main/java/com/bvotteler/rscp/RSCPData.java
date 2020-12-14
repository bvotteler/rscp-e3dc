package com.bvotteler.rscp;

import com.bvotteler.rscp.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bvotteler.rscp.util.ByteUtils.bytesToShort;
import static com.bvotteler.rscp.util.ByteUtils.copyBytesIntoNewArray;
import static com.bvotteler.rscp.util.ByteUtils.intToBytes;
import static com.bvotteler.rscp.util.ByteUtils.longToBytes;
import static com.bvotteler.rscp.util.ByteUtils.reverseByteArray;
import static com.bvotteler.rscp.util.ByteUtils.truncateFirstNBytes;

public class RSCPData {
    private static final Logger logger = LoggerFactory.getLogger(RSCPData.class);
    // byte sizes
    private static final int sizeDataTag = 4;
    private static final int sizeDataType = 1;
    private static final int sizeDataLength = 2;

    // byte offset structure (number of bytes counting from zero)
    // first 4 bytes: namespace identifier (1 byte) and data tag of what the data request/response is related to
    // next 1 bytes: data type (string, int, container, etc)
    // next 2 bytes: data length in bytes
    // next ? bytes: data portion of variable length
    // last 4 bytes: CRC checksum (if applicable)
    private static final int offsetDataTag = 0;
    private static final int offsetDataType = offsetDataTag + sizeDataTag;
    private static final int offsetDataLength = offsetDataType + sizeDataType;
    private static final int offsetData = offsetDataLength + sizeDataLength;

    private short dataLength;
    private byte[] data; // unknown size

    private RSCPTag dataTag;
    private RSCPDataType dataType;

    public static List<RSCPData> of(byte[] bytes) {
        if (bytes == null || bytes.length < offsetData) {
            logger.warn("Not enough bytes to form RSCPData instance(s), returning empty list (data truncated?).");
            return Collections.emptyList();
        }

        List<RSCPData> rscpDataList = new ArrayList<>();

        RSCPData rscpData = new RSCPData();

        byte[] tagNameBytes = copyBytesIntoNewArray(bytes, offsetDataTag, sizeDataTag);
        rscpData.setDataTag(RSCPTag.getTagForBytes(reverseByteArray(tagNameBytes)));

        // single byte, no need to reverse
        RSCPDataType dataType = RSCPDataType.getDataTypeForBytes(bytes[offsetDataType]);

        byte[] lengthBytes = copyBytesIntoNewArray(bytes, offsetDataLength, sizeDataLength);
        short dataLength = bytesToShort(reverseByteArray(lengthBytes));

        if (bytes.length < offsetData + dataLength) {
            logger.warn("Not enough bytes in data section to form complete RSCPValue instance (data truncated?)");
            return Collections.emptyList();
        }

        byte[] data = copyBytesIntoNewArray(bytes, offsetData, dataLength);
        rscpData.setDataWithType(data, dataType);

        rscpDataList.add(rscpData);

        // more left to process?
        if (bytes.length > offsetData + dataLength) {
            // truncate bytes and start recursion
            byte[] remainingBytes = truncateFirstNBytes(bytes, offsetData + dataLength);
            rscpDataList.addAll(RSCPData.of(remainingBytes));
        }

        return rscpDataList;
    }

    public byte[] getAsBytes() {
        byte[] bytes = new byte[offsetData + dataLength];
        // copy over to final position and reverse
        System.arraycopy(reverseByteArray(getDataTagAsBytes()), 0, bytes, 0, sizeDataTag);
        System.arraycopy(reverseByteArray(getDataTypeAsBytes()), 0, bytes, offsetDataType, sizeDataType);
        System.arraycopy(reverseByteArray(ByteUtils.shortToBytes(dataLength)), 0, bytes, offsetDataLength, sizeDataLength);
        System.arraycopy(data, 0, bytes, offsetData, dataLength);
        return bytes;
    }

    public int getByteCount() {
        return offsetData + dataLength;
    }

    public int getDataByteCount() {
        return dataLength;
    }

    public void appendData(byte[] bytes) {
        int oldLength = this.dataLength;
        int additionalLength = bytes.length;
        // pretty expensive because we copy and create new.
        // TODO: this can be optimized using builders
        byte[] newData = new byte[oldLength + additionalLength];
        if (oldLength > 0) {
            System.arraycopy(this.data, 0, newData, 0, oldLength);
        }
        if (additionalLength > 0) {
            System.arraycopy(bytes, 0, newData, oldLength, additionalLength);
        }
        this.data = newData;
        this.dataLength += additionalLength;
    }

    public void appendData(RSCPData value) {
        appendData(value.getAsBytes());
    }

    public void appendData(List<RSCPData> values) {
        for (RSCPData value : values) {
            appendData(value);
        }
    }

    public byte[] getData() {
        return data;
    }

    // a few helpers for some simple types
    public void setData(RSCPData value) {
        setDataWithType(value.getAsBytes(), RSCPDataType.CONTAINER);
    }

    public void setData(String value) {
        setDataWithType(value.getBytes(), RSCPDataType.STRING);
    }

    public void setData(short value) {
        setDataWithType(reverseByteArray(intToBytes(value)), RSCPDataType.INT16);
    }

    public void setData(int value) {
        setDataWithType(reverseByteArray(intToBytes(value)), RSCPDataType.INT16);
    }

    public void setData(long value) {
        setDataWithType(reverseByteArray(longToBytes(value)), RSCPDataType.INT32);
    }

    public void setData(boolean value) {
        byte[] flag = new byte[1];
        flag[0] = (byte) ((value) ? 0xFF : 0x00);
        setDataWithType(flag, RSCPDataType.BOOL);
    }

    public void setDataWithType(byte[] bytes, RSCPDataType dataType) {
        this.data = bytes;
        this.dataLength = (short) data.length;
        setDataType(dataType);
    }

    public void setTimeStampData(long seconds, int nanos) {
        byte[] timestamp = new byte[12];
        System.arraycopy(reverseByteArray(longToBytes(seconds)), 0, timestamp, 0, 8);
        // ignore nano seconds
        System.arraycopy(reverseByteArray(intToBytes(nanos)), 0, timestamp, 8, 4);
        setDataWithType(timestamp, RSCPDataType.TIMESTAMP);
    }

    // TODO: could add more setData helpers.

    private byte[] getDataTagAsBytes() {
        return dataTag.getValueAsBytes();
    }

    public RSCPTag getDataTag() {
        return dataTag;
    }

    public void setDataTag(RSCPTag dataTag) {
        this.dataTag = dataTag;
    }

    private byte[] getDataTypeAsBytes() {
        byte[] bytes = new byte[1];
        bytes[0] = dataType.getValue();
        return bytes;
    }

    public RSCPDataType getDataType() {
        return dataType;
    }

    public void setDataType(RSCPDataType dataType) {
        this.dataType = dataType;
    }

    public List<RSCPData> getContainerData() {
        if (RSCPDataType.CONTAINER != getDataType()) {
            return Collections.emptyList();
        } else {
            return RSCPData.of(getData());
        }
    }
}
