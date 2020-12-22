package io.github.bvotteler.rscp;

import io.github.bvotteler.rscp.util.ByteUtils;
import org.hamcrest.Matchers;
import org.junit.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.github.bvotteler.rscp.RSCPFrame.*;
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
            int idx = offsetTsSeconds + i;
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
        assertThat(frameFromBytes.getTimestamp(), equalTo(Instant.ofEpochSecond(14)));

        RSCPData container = frameFromBytes.getData().get(0);
        assertThat(container.getDataTag(), is(RSCPTag.TAG_DB_REQ_HISTORY_DATA_DAY));
        assertThat(container.getDataType(), is(RSCPDataType.CONTAINER));

        List<RSCPData> containerContents = container.getContainerData();
        assertThat(containerContents, hasSize(3));

        List<RSCPTag> containerContentTags = containerContents.stream()
                .map(RSCPData::getDataTag)
                .collect(Collectors.toList());
        assertThat(containerContentTags, Matchers.hasItems(RSCPTag.TAG_DB_REQ_HISTORY_TIME_START, RSCPTag.TAG_DB_REQ_HISTORY_TIME_SPAN, RSCPTag.TAG_DB_REQ_HISTORY_TIME_INTERVAL));

        RSCPData timeStartData = containerContents.stream()
                .filter(reqData -> reqData.getDataTag() == RSCPTag.TAG_DB_REQ_HISTORY_TIME_START)
                .findFirst()
                .get();

        assertThat(timeStartData.getValueAsInstant(), equalTo(Optional.of(Instant.ofEpochSecond(timeStart))));
    }

    @Test
    public void knownAuthResponseToFrame() {
        RSCPFrame frame = RSCPFrame.builder().buildFromRawBytes(getSampleAuthResponseMessage());

        Instant expectedTimestamp = Instant.parse("2016-10-14T04:01:03.035138Z");
        assertThat(frame.getTimestamp(), equalTo(expectedTimestamp));

        List<RSCPData> dataList = frame.getData();
        assertThat(dataList, hasSize(1));

        RSCPData data = dataList.get(0);
        assertThat(data.getDataTag(), equalTo(RSCPTag.TAG_RSCP_AUTHENTICATION));
        assertThat(data.getValueAsInt(), equalTo(Optional.of(10)));
    }

    @Test
    public void knownDbRequestToFrame() {
        RSCPFrame frame = builder().buildFromRawBytes(getKnownDBRequestFrame());

        List<RSCPData> frameData = frame.getData();
        assertThat(frameData, hasSize(1));
        assertThat(frameData.get(0).getDataType(), equalTo(RSCPDataType.CONTAINER));

        List<RSCPData> containerData = frameData.get(0).getContainerData();
        assertThat(containerData, hasSize(3));

        RSCPData reqStartTimeData = containerData.stream()
                .filter(data -> data.getDataTag() == RSCPTag.TAG_DB_REQ_HISTORY_TIME_START)
                .findFirst()
                .get();
        long expectedEpochSeconds = 1505309400; // request starttime value 2017-09-13T13:30:00Z
        assertThat(reqStartTimeData.getValueAsInstant(), equalTo(Optional.of(Instant.ofEpochSecond(expectedEpochSeconds))));

        RSCPData reqIntervalData = containerData.stream()
                .filter(data -> data.getDataTag() == RSCPTag.TAG_DB_REQ_HISTORY_TIME_INTERVAL)
                .findFirst()
                .get();

        long expectedIntervalSeconds = 900; // known request interval value
        assertThat(reqIntervalData.getValueAsDuration(), equalTo(Optional.of(Duration.ofSeconds(expectedIntervalSeconds))));

        RSCPData reqTimeSpanData = containerData.stream()
                .filter(data -> data.getDataTag() == RSCPTag.TAG_DB_REQ_HISTORY_TIME_SPAN)
                .findFirst()
                .get();
        long expectedTimeSpanSeconds = expectedIntervalSeconds; // known to be the same as interval
        assertThat(reqTimeSpanData.getValueAsDuration(), equalTo(Optional.of(Duration.ofSeconds(expectedTimeSpanSeconds))));

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
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.longToBytes(tsSeconds)), 0, frame, offsetTsSeconds, sizeTsSeconds);
        System.arraycopy(ByteUtils.reverseByteArray(ByteUtils.intToBytes(tsNanoSeconds)), 0, frame, offsetTsNanoSeconds, sizeTsNanoSeconds);

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

    private byte[] getSampleAuthResponseMessage() {
        final String testAuthResponse = "e3 dc 00 11 7f 58 00 58 00 00 00 00 d0 29 18 02 08 00 01 00 80 00 03 01 00 0a b2 34 f2 4d 00 00".replaceAll("\\s+", "");
        return ByteUtils.hexStringToByteArray(testAuthResponse);
    }

    private byte[] getKnownDBRequestFrame() {
        final String testDBReqFrame = "e3 dc 00 11 fa 12 c2 59 00 00 00 00 c0 72 4c 12 40 00 00 01 00 06 0e 39 00 01 01 00 06 0f 0c 00 d8 32 b9 59 00 00 00 00 00 00 00 00 02 01 00 06 0f 0c 00 84 03 00 00 00 00 00 00 00 00 00 00 03 01 00 06 0f 0c 00 84 03 00 00 00 00 00 00 00 00 00 00 2d 56 ff ec".replaceAll("\\s+", "");
        return ByteUtils.hexStringToByteArray(testDBReqFrame);
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
