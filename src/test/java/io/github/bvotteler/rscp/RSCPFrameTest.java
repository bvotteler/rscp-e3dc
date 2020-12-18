package io.github.bvotteler.rscp;

import io.github.bvotteler.rscp.util.ByteUtils;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class RSCPFrameTest {

    @Test
    public void testAuthFrameIsCorrect() {
        byte[] expected = getKnownAuthFrameForTestCreds();

        byte[] actual = buildAuthenticationMessage("testuser@example.com", "SuperSecret123");

        assertThat(actual.length, equalTo(expected.length));

        // ignore checksum and time stamps, set those to 00.
        for (int i = 0; i < RSCPFrame.sizeTsSeconds; i++) {
            int idx = RSCPFrame.offsetTsSeconds + i;
            expected[idx] = (byte) 0x00;
            actual[idx] = (byte) 0x00;
        }

        for (int i = 0; i < RSCPFrame.sizeTsNanoSeconds; i++) {
            int idx = RSCPFrame.offsetTsNanoSeconds + i;
            expected[idx] = (byte) 0x00;
            actual[idx] = (byte) 0x00;
        }

        int offsetCRC = actual.length - RSCPFrame.sizeCRC;
        for (int i = 0; i < RSCPFrame.sizeCRC; i++) {
            int idx = offsetCRC + i;
            expected[idx] = (byte) 0x00;
            actual[idx] = (byte) 0x00;
        }

        assertThat(actual, is(expected));
    }

    @Test
    public void knownDataRequestFrameToBytesToFrame() {
        // using a "known" request frame pattern
        byte[] reqFrame = buildDataBaseRequestMessageFromTemplate(42, 900, 900);

        RSCPFrame actual = RSCPFrame.builder().buildFromRawBytes(reqFrame);

        assertThat(actual.getData(), hasSize(1));
        RSCPData container = actual.getData().get(0);
        // should be requesting history data for a day
        assertThat(container.getDataTag(), is(RSCPTag.TAG_DB_REQ_HISTORY_DATA_DAY));
        assertThat(container.getDataType(), is(RSCPDataType.CONTAINER));

        // the request container should contain "time start", "time span", and "time interval"
        List<RSCPData> containerContents = container.getContainerData();
        assertThat(containerContents, hasSize(3));

        // collect actual tags
        List<RSCPTag> actualTags = containerContents.stream()
                .map(RSCPData::getDataTag)
                .collect(Collectors.toList());

        assertThat(actualTags, hasItems(RSCPTag.TAG_DB_REQ_HISTORY_TIME_START, RSCPTag.TAG_DB_REQ_HISTORY_TIME_SPAN, RSCPTag.TAG_DB_REQ_HISTORY_TIME_INTERVAL));
    }

    @Test
    public void constructDeconstructRequestFrame() {
        long timeStart = 42;
        long interval = 900;
        long timeSpan = 3 * interval; // 3 intervals.

        // build request starting with a container
        RSCPData reqContainer = RSCPDataTest.buildSampleDBRequestContainer(Instant.ofEpochSecond(timeStart), Duration.ofSeconds(interval), Duration.ofSeconds(timeSpan));

        // build frame and append the request container
        RSCPFrame reqFrame = RSCPFrame.builder()
                .addData(reqContainer)
                .timestamp(Instant.ofEpochSecond(14))
                .withChecksum()
                .build();
        byte[] requestWithTS = reqFrame.getAsByteArray();

        // reverse: feed bytes to create a frame instance to be inspected.
        RSCPFrame frameFromBytes = RSCPFrame.builder().buildFromRawBytes(requestWithTS);

        assertThat(frameFromBytes.getData(), hasSize(1));

        RSCPData container = frameFromBytes.getData().get(0);
        assertThat(container.getDataTag(), is(RSCPTag.TAG_DB_REQ_HISTORY_DATA_DAY));
        assertThat(container.getDataType(), is(RSCPDataType.CONTAINER));

        List<RSCPData> containerContents = container.getContainerData();
        assertThat(containerContents, hasSize(3));

        List<RSCPTag> containerContentTags = containerContents.stream()
                .map(RSCPData::getDataTag)
                .collect(Collectors.toList());
        assertThat(containerContentTags, Matchers.hasItems(RSCPTag.TAG_DB_REQ_HISTORY_TIME_START, RSCPTag.TAG_DB_REQ_HISTORY_TIME_SPAN, RSCPTag.TAG_DB_REQ_HISTORY_TIME_INTERVAL));
    }

    private byte[] getKnownAuthFrameForTestCreds() {
        // built using 'testuser@example.com' and 'SuperSecret123'
        String template = "E3DC00114D61D45F0000000000CEED343700010000000E3000020000000D14007465737475736572406578616D706C652E636F6D030000000D0E00537570657253656372657431323360C48640";

        return ByteUtils.hexStringToByteArray(template);
    }

    private byte[] buildDataBaseRequestMessageFromTemplate(long timeStart, long interval, long timeSpan) {
        // copied this from some real comms to have a reference.
        String template = "E3 DC 00 11 00 00 00 00 00 00 00 00 00 00 00 00 40 00 00 01 00 06 0E 39 00 01 01 00 06 0F 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 02 01 00 06 0F 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 03 01 00 06 0F 0C 00 00 00 00 00 00 00 00 00 00 00 00 00 AA AA AA AA".replaceAll("\\s+", "");
        byte[] frame = ByteUtils.hexStringToByteArray(template);
        // insert time stamp
        // seconds since 01/01/1970 from byte 4 to 11 (8 bytes)
        // nanoseconds from byte 12 to 15 (4 bytes)

        // get system time stamp
        long tsInMillis = System.currentTimeMillis();
        long tsSeconds = tsInMillis / 1000;
        int tsNanoSeconds = (int) ((tsInMillis % 1000) * 1000 * 1000);
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.longToBytes(tsSeconds)), 0, frame, 4, 8);
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.intToBytes(tsNanoSeconds)), 0, frame, 12, 4);

        // insert timestart value (offset: 32), ignore nano seconds
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.longToBytes(timeStart)), 0, frame, 32, 8);
        // insert interval value (offset: 51), ignore nano seconds
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.longToBytes(interval)), 0, frame, 51, 8);
        // insert timespan value (offset: 70), ignore nano seconds
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.longToBytes(timeSpan)), 0, frame, 70, 8);

        // recalculate CRC checksum (last 4 bytes)
        int checksum = ByteUtils.calculateCRC32Checksum(frame, 0, frame.length - 4);
        // reverse all bytes except for CRC
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.intToBytes(checksum)), 0, frame, frame.length - 4, 4);

        return frame;
    }

    private byte[] buildAuthenticationMessage(String user, String password) {
        RSCPData authUser = RSCPData.builder()
                .tag(RSCPTag.TAG_RSCP_AUTHENTICATION_USER)
                .stringValue(user)
                .build();

        RSCPData authPwd = RSCPData.builder()
                .tag(RSCPTag.TAG_RSCP_AUTHENTICATION_PASSWORD)
                .stringValue(password)
                .build();

        RSCPData authContainer = RSCPData.builder()
                .tag(RSCPTag.TAG_RSCP_REQ_AUTHENTICATION)
                .containerValues(Arrays.asList(authUser, authPwd))
                .build();

        RSCPFrame authFrame = RSCPFrame.builder()
                .addData(authContainer)
                .timestamp(Instant.now())
                .build();
        return authFrame.getAsByteArray();
    }
}
