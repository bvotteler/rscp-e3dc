package com.bvotteler.rscp;

import com.bvotteler.rscp.util.ByteUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.bvotteler.rscp.util.ByteUtils.*;

public class RSCPFrame {
    // byte sizes
    public static final int sizeMagic = 2;
    public static final int sizeCtrl = 2;
    public static final int sizeTsSeconds = 8;
    public static final int sizeTsNanoSeconds = 4;
    public static final int sizeLength = 2;
    // note: data size goes here, but is unknown as it is variable
    public static final int sizeCRC = 4;
    // byte offset structure (number of bytes counting from zero)
    // first 2 bytes: magic bytes (E3DC)
    // next 2 bytes: control bytes (version, crc included flag, etc)
    // next 8 bytes: time stamp in seconds (since 1970-01-01 00:00:00)
    // next 4 bytes: time stamp nanoseconds (elapsed since last second)
    // next 2 bytes: frame length
    // next ? bytes: data portion of variable length
    // last 4 bytes: CRC checksum if included (if bit 23 is set)
    public static final int offsetMagic = 0;
    public static final int offsetCtrl = offsetMagic + sizeMagic;
    public static final int offsetTsSeconds = offsetCtrl + sizeCtrl;
    public static final int offsetTsNanoSeconds = offsetTsSeconds + sizeTsSeconds;
    public static final int offsetLength = offsetTsNanoSeconds + sizeTsNanoSeconds;
    public static final int offsetData = offsetLength + sizeLength;
    // used to make sure we have a minimum size of bytes to work with
    public static final int minimumFrameSize = offsetData;
    private static final byte[] magicBytes = ByteUtils.hexStringToByteArray("E3DC");
    private byte[] controlBytes = new byte[sizeCtrl];
    private long tsSeconds;
    private int tsNanoSeconds;
    private short dataByteCount;
    private List<RSCPData> data = new ArrayList<RSCPData>();
    private int checksum;

    // constructor
    public RSCPFrame() {
        // set some basics
        setControlBytesToDefault();
    }

    public static RSCPFrame of(byte[] bytes) {
        validateBytesCanBeFrameElseThrow(bytes);

        RSCPFrame frame = new RSCPFrame();

        byte[] control = copyBytesIntoNewArray(bytes, offsetCtrl, sizeCtrl);
        frame.setControlBytes(control);

        byte[] secs = copyBytesIntoNewArray(bytes, offsetTsSeconds, sizeTsSeconds);
        long seconds = bytesToLong(reverseByteArray(secs));
        byte[] nanos = copyBytesIntoNewArray(bytes, offsetTsNanoSeconds, sizeTsNanoSeconds);
        int nanoSecs = bytesToInt(reverseByteArray(nanos));
        frame.setTimestamp(seconds, nanoSecs);

        byte[] length = copyBytesIntoNewArray(bytes, offsetLength, sizeLength);
        short dataLength = bytesToShort(reverseByteArray(length));

        byte[] data = copyBytesIntoNewArray(bytes, offsetData, dataLength);

        List<RSCPData> rscpDataList = RSCPData.of(data);
        frame.appendData(rscpDataList);

        return frame;
    }

    private static void validateBytesCanBeFrameElseThrow(byte[] bytes) {
        if (bytes == null || bytes.length < offsetData) {
            throw new IllegalArgumentException("Byte array is null, or too small to be a frame.");
        }

        byte[] frameMagicBytes = copyBytesIntoNewArray(bytes, offsetMagic, sizeMagic);
        if (!Arrays.equals(frameMagicBytes, magicBytes)) {
            throw new IllegalArgumentException("Byte array does not contain magic bytes.");
        }

        byte[] frameLengthBytes = copyBytesIntoNewArray(bytes, offsetLength, sizeLength);
        short frameDataLength = bytesToShort(reverseByteArray(frameLengthBytes));
        if (frameDataLength < 0) {
            throw new IllegalArgumentException("Frame data length value is less than zero.");
        }
    }

    private void setControlBytesToDefault() {
        this.controlBytes = ByteUtils.hexStringToByteArray("1100");
    }

    private void setControlBytes(byte[] bytes) {
        this.controlBytes = bytes;
    }

    /**
     * Get frame content as bytes. Will calculate and insert checksum CRC if needed.
     *
     * @param setTimeStampToNow If true, it will use System.currentTimeMillis() to set frame timestamp.
     * @return Byte array ready to be encrypted and sent.
     */
    public byte[] getAsBytes(boolean setTimeStampToNow) {
        if (setTimeStampToNow) {
            setTimeStampToNow();
        }

        // calculate size needed
        int sizeNeeded = getFrameByteCount();

        byte[] bytes = new byte[sizeNeeded];
        // first, add in magic bytes
        System.arraycopy(magicBytes, 0, bytes, offsetMagic, sizeMagic);
        // then control bytes
        System.arraycopy(reverseByteArray(controlBytes), 0, bytes, offsetCtrl, sizeCtrl);
        // then timestamps
        System.arraycopy(reverseByteArray(ByteUtils.longToBytes(tsSeconds)), 0, bytes, offsetTsSeconds, sizeTsSeconds);
        System.arraycopy(reverseByteArray(ByteUtils.intToBytes(tsNanoSeconds)), 0, bytes, offsetTsNanoSeconds, sizeTsNanoSeconds);
        // then length of data
        System.arraycopy(reverseByteArray(ByteUtils.shortToBytes(dataByteCount)), 0, bytes, offsetLength, sizeLength);
        // then data
        int dataOffset = offsetData;
        for (RSCPData value : data) {
            int byteSize = value.getByteCount();
            System.arraycopy(value.getAsBytes(), 0, bytes, dataOffset, byteSize);
            dataOffset += byteSize; // move offset along
        }

        // now dataOffset points to end of data, so use it for next guy
        final int offsetEndOfData = dataOffset;

        // finally, recalculate and add checksum if needed
        if (isChecksumBitSet()) {
            checksum = ByteUtils.calculateCRC32Checksum(bytes, 0, offsetEndOfData);
            System.arraycopy(reverseByteArray(ByteUtils.intToBytes(checksum)), 0, bytes, offsetEndOfData, sizeCRC);
        }

        return bytes;
    }

    /**
     * Get frame content as bytes. Will calculate and insert checksum CRC if needed.
     * <p>
     * Note: Will NOT refresh the timestamp for the frame.
     *
     * @return Byte array ready to be encrypted and sent.
     */
    public byte[] getAsBytes() {
        return getAsBytes(false);
    }

    public void appendData(RSCPData value) {
        data.add(value);
        // update length of data
        dataByteCount += value.getByteCount();
    }

    public void appendData(List<RSCPData> valueList) {
        for (RSCPData value : valueList) {
            appendData(value);
        }
    }

    public List<RSCPData> getData() {
        return data;
    }

    public long getTsSeconds() {
        return tsSeconds;
    }

    public void setTimestamp(long seconds, int nanoSeconds) {
        this.tsSeconds = seconds;
        this.tsNanoSeconds = nanoSeconds;
    }

    public int getTsNanoSeconds() {
        return tsNanoSeconds;
    }

    public boolean isChecksumBitSet() {
        // grab first ctrl byte
        byte ctrlPart1 = this.controlBytes[0];
        // the 4th least significant bit is the CRC flag
        int bitPosition = 4;  // Position of this bit in a byte

        return (ctrlPart1 >> bitPosition & 1) == 1;
    }

    public int getFrameByteCount() {
        return offsetData + getDataByteCount() + (isChecksumBitSet() ? sizeCRC : 0);
    }

    // adds up indicated byte size of all entries in data (RSCPValue instances) and returns that value
    public int getDataByteCount() {
        int size = 0;
        for (RSCPData value : data) {
            size += value.getByteCount();
        }
        return size;
    }

    public void setChecksumBitTo(boolean flag) {
        int bitPosition = 4;
        controlBytes[0] = (flag) ?
                ((byte) (controlBytes[0] | (1 << bitPosition)))
                : ((byte) (controlBytes[0] & ~(1 << bitPosition)));
    }

    public int getChecksum() {
        return checksum;
    }

    public void setTimeStampToNow() {
        // get system time stamp
        long tsInMillis = System.currentTimeMillis();
        tsSeconds = tsInMillis / 1000;
        tsNanoSeconds = (int) ((tsInMillis % 1000) * 1000 * 1000);
    }
}
