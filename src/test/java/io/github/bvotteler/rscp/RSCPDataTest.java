package io.github.bvotteler.rscp;

import io.github.bvotteler.rscp.util.ByteUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.fail;

public class RSCPDataTest {
    @Test(expected = IllegalStateException.class)
    public void builder__validation_fails_if_tag_is_missing() {
        RSCPData.builder().boolValue(true).build();
        fail("Expected exception to have been thrown.");
    }

    @Test(expected = IllegalStateException.class)
    public void builder__validation_fails_if_value_is_missing() {
        RSCPData.builder().tag(RSCPTag.TAG_DB_REQ_HISTORY_DATA_DAY).valueOfType(RSCPDataType.INT16, null).build();
        fail("Expected exception to have been thrown.");
    }

    @Test(expected = IllegalStateException.class)
    public void builder__validation_fails_if_data_type_is_missing() {
        RSCPData.builder().tag(RSCPTag.TAG_DB_REQ_HISTORY_DATA_DAY).valueOfType(null, "yolo".getBytes(StandardCharsets.UTF_8)).build();
        fail("Expected exception to have been thrown.");
    }

    @Test
    public void builder_validation_passes_if_value_is_missing_or_empty_for_none_type() {
        RSCPData noneData1 = RSCPData.builder().tag(RSCPTag.TAG_EMS_REQ_POWER_PV).valueOfType(RSCPDataType.NONE, new byte[0]).build();
        RSCPData noneData2 = RSCPData.builder().tag(RSCPTag.TAG_EMS_REQ_POWER_PV).noneValue().build();

        assertThat(noneData1.getDataType(), equalTo(RSCPDataType.NONE));
        assertThat(noneData2.getDataType(), equalTo(RSCPDataType.NONE));

        assertThat(noneData1.getValueAsByteArray().length, equalTo(0));
        assertThat(noneData1, equalTo(noneData2));
    }

    @Test
    public void none__can_be_serialized_and_deserialized() {
        RSCPData noneData = RSCPData.builder().tag(RSCPTag.TAG_EMS_REQ_POWER_PV).noneValue().build();
        byte[] noneRaw = noneData.getAsByteArray();

        List<RSCPData> noneDataDeserialized = RSCPData.builder().buildFromRawBytes(noneRaw);

        assertThat(noneDataDeserialized, hasSize(1));
        assertThat(noneData, equalTo(noneDataDeserialized.get(0)));

    }

    @Test
    public void builder__from_raw() {
        RSCPData container = buildSampleDBRequestContainer(Instant.ofEpochSecond(42L), Duration.ofSeconds(900L), Duration.ofSeconds(900L));

        // contains the inner RSCPData instances as byte array
        byte[] raw = container.getValueAsByteArray();

        List<RSCPData> fromRaw = RSCPData.builder().buildFromRawBytes(raw);

        assertThat(container.getContainerData(), equalTo(fromRaw));
    }

    @Test
    public void knownContainerBytesToData() {
        List<RSCPData> dataList = RSCPData.builder().buildFromRawBytes(getSampleDBResponseContainerData());

        assertThat(dataList, hasSize(13));

        // pick a few to validate
        RSCPData batPowerIn = dataList.get(0);
        assertThat(batPowerIn.getDataTag(), equalTo(RSCPTag.TAG_DB_BAT_POWER_IN));
        assertThat(batPowerIn.getValueAsFloat(), equalTo(Optional.of(0.0F)));

        RSCPData batPowerOut = dataList.get(1);
        assertThat(batPowerOut.getDataTag(), equalTo(RSCPTag.TAG_DB_BAT_POWER_OUT));
        assertThat(batPowerOut.getValueAsFloat(), equalTo(Optional.of(232.0F)));

        RSCPData batCycleCount = dataList.get(9);
        assertThat(batCycleCount.getDataTag(), equalTo(RSCPTag.TAG_DB_BAT_CYCLE_COUNT));
        assertThat(batCycleCount.getValueAsInt(), equalTo(Optional.of(349)));
        assertThat(batCycleCount.getValueAsLong(), equalTo(Optional.of(349L)));

        RSCPData autarky = dataList.get(11);
        assertThat(autarky.getDataTag(), equalTo(RSCPTag.TAG_DB_AUTARKY));
        assertThat(autarky.getValueAsString(), equalTo(Optional.of(String.format("%.2f", 96.84))));

        // another container with 13 entries
        RSCPData container = dataList.get(12);
        assertThat(container.getDataType(), CoreMatchers.equalTo(RSCPDataType.CONTAINER));
        assertThat(container.getContainerData(), hasSize(13));
    }

    @Test
    public void testInt32NegativeRegression() {
        // -427 in 32-bit hex is 0xFFFFFE55. In LE: 0x55, 0xFE, 0xFF, 0xFF
        byte[] bytes = new byte[] { (byte) 0x55, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF };

        RSCPData data = new RSCPData(null, RSCPDataType.INT32, bytes);

        // Verifies the fix for the reported bug
        assertThat(data.getValueAsInt(), equalTo(Optional.of(-427)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("-427")));
    }

    @Test
    public void testChar8Signed() {
        RSCPData data = new RSCPData(null, RSCPDataType.CHAR8, new byte[]{(byte) 0x80});

        assertThat(data.getValueAsString(), equalTo(Optional.of("-128")));
    }

    @Test
    public void testUChar8Unsigned() {
        RSCPData data = new RSCPData(null, RSCPDataType.UCHAR8, new byte[]{(byte) 0xFF});

        assertThat(data.getValueAsString(), equalTo(Optional.of("255")));
    }

    @Test
    public void testInt16() {
        byte[] bytes = createLeBytes(Short.BYTES, -32768);
        RSCPData data = new RSCPData(null, RSCPDataType.INT16, bytes);

        assertThat(data.getValueAsShort(), equalTo(Optional.of((short) -32768)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("-32768")));
    }

    @Test
    public void testUInt16() {
        byte[] bytes = createLeBytes(Short.BYTES, 65535);
        RSCPData data = new RSCPData(null, RSCPDataType.UINT16, bytes);

        assertThat(data.getValueAsShort(), equalTo(Optional.of((short) -1)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("65535")));
    }

    @Test
    public void testUInt32() {
        byte[] bytes = createLeBytes(Integer.BYTES, 4294967295L);
        RSCPData data = new RSCPData(null, RSCPDataType.UINT32, bytes);

        assertThat(data.getValueAsInt(), equalTo(Optional.of(-1)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("4294967295")));
    }

    @Test
    public void testInt32Positive() {
        // 123,456,789 in hex is 0x075BCD15.
        // In Little Endian layout: 0x15, 0xCD, 0x5B, 0x07
        byte[] bytes = new byte[] { (byte) 0x15, (byte) 0xCD, (byte) 0x5B, (byte) 0x07 };

        RSCPData data = new RSCPData(null, RSCPDataType.INT32, bytes);

        assertThat(data.getValueAsInt(), equalTo(Optional.of(123456789)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("123456789")));
    }

    @Test
    public void testInt32Negative() {
        // -427 in 32-bit hex (Two's Complement) is 0xFFFFFE55.
        // Ordered in Little Endian: 0x55, 0xFE, 0xFF, 0xFF
        byte[] bytes = new byte[] { (byte) 0x55, (byte) 0xFE, (byte) 0xFF, (byte) 0xFF };

        RSCPData data = new RSCPData(null, RSCPDataType.INT32, bytes);

        // Asserts that the 4-byte LE pattern decodes cleanly to a signed int primitive
        assertThat(data.getValueAsInt(), equalTo(Optional.of(-427)));

        // Asserts that String formatting correctly keeps the minus sign and value
        assertThat(data.getValueAsString(), equalTo(Optional.of("-427")));
    }

    @Test
    public void testInt64Positive() {
        // 5,000,000,000 in 64-bit Long. Hex: 0x000000012A05F200
        // In Little Endian layout: 0x00, 0xF2, 0x05, 0x2A, 0x01, 0x00, 0x00, 0x00
        byte[] bytes = new byte[] {
            (byte) 0x00, (byte) 0xF2, (byte) 0x05, (byte) 0x2A,
            (byte) 0x01, (byte) 0x00, (byte) 0x00, (byte) 0x00
        };

        RSCPData data = new RSCPData(null, RSCPDataType.INT64, bytes);

        assertThat(data.getValueAsLong(), equalTo(Optional.of(5000000000L)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("5000000000")));
    }

    @Test
    public void testInt64Negative() {
        // -5,000,000,000 (Negative 5 Billion) in 64-bit hex (Two's Complement): 0xFFFFFFFED5FA0E00
        // In Little Endian layout: 0x00, 0x0E, 0xFA, 0xD5, 0xFE, 0xFF, 0xFF, 0xFF
        byte[] bytes = new byte[] {
            (byte) 0x00, (byte) 0x0E, (byte) 0xFA, (byte) 0xD5,
            (byte) 0xFE, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };

        RSCPData data = new RSCPData(null, RSCPDataType.INT64, bytes);

        assertThat(data.getValueAsLong(), equalTo(Optional.of(-5000000000L)));
        assertThat(data.getValueAsString(), equalTo(Optional.of("-5000000000")));
    }

    @Test
    public void testSizeUpscaling() {
        byte[] shortPayload = new byte[]{ 0x01, 0x02 };
        RSCPData data = new RSCPData(null, RSCPDataType.INT16, shortPayload);

        // A Short should seamlessly upscale into an Int and Long
        assertThat(data.getValueAsShort(), equalTo(Optional.of((short) 513)));
        assertThat(data.getValueAsInt(), equalTo(Optional.of(513)));
        assertThat(data.getValueAsLong(), equalTo(Optional.of(513L)));
    }

    @Test
    public void testInvalidByteArray() {
        byte[] oversizedPayload = new byte[]{ 1, 2, 3, 4, 5 };
        RSCPData data = new RSCPData(null, RSCPDataType.INT32, oversizedPayload);

        // 5 bytes won't fit into 4-byte Int, expect empty
        assertThat(data.getValueAsInt(), equalTo(Optional.empty()));

        byte[] emptyPayload = new byte[0];
        RSCPData emptyShort = new RSCPData(null, RSCPDataType.INT16, emptyPayload);
        assertThat(emptyShort.getValueAsShort(), equalTo(Optional.empty()));
        assertThat(emptyShort.getValueAsInt(), equalTo(Optional.empty()));
        assertThat(emptyShort.getValueAsLong(), equalTo(Optional.empty()));
    }

    private byte[] getSampleDBResponseContainerData() {
        final String testContainerData = "02 00 80 06 0a 04 00 00 00 00 00 03 00 80 06 0a 04 00 00 00 68 43 04 00 80 06 0a 04 00 00 00 04 43 05 00 80 06 0a 04 00 00 00 e0 40 06 00 80 06 0a 04 00 00 00 40 41 07 00 80 06 0a 04 00 00 00 be 43 08 00 80 06 0a 04 00 00 00 00 00 09 00 80 06 0a 04 00 00 00 00 00 0a 00 80 06 0a 04 00 00 00 e8 41 0b 00 80 06 06 04 00 5d 01 00 00 0c 00 80 06 0a 04 00 c8 55 c4 42 0d 00 80 06 0a 04 00 28 af c1 42 20 00 80 06 0e 8f 00 01 00 80 06 0a 04 00 00 00 00 00 02 00 80 06 0a 04 00 00 00 00 00 03 00 80 06 0a 04 00 00 00 00 00 04 00 80 06 0a 04 00 00 00 00 00 05 00 80 06 0a 04 00 00 00 00 00 06 00 80 06 0a 04 00 00 00 00 00 07 00 80 06 0a 04 00 00 00 00 00 08 00 80 06 0a 04 00 00 00 00 00 09 00 80 06 0a 04 00 00 00 00 00 0a 00 80 06 0a 04 00 00 00 f4 41 0b 00 80 06 06 04 00 5d 01 00 00 0c 00 80 06 0a 04 00 00 00 c8 42 0d 00 80 06 0a 04 00 00 00 c8 42".replaceAll("\\s+", "");
        return ByteUtils.hexStringToByteArray(testContainerData);
    }

    public static RSCPData buildSampleDBRequestContainer(Instant timeStart, Duration interval, Duration timeSpan) {
        // build parameters
        RSCPData reqTimeStart = RSCPData.builder()
                .tag(RSCPTag.TAG_DB_REQ_HISTORY_TIME_START)
                .timestampValue(timeStart)
                .build();

        RSCPData reqInterval = RSCPData.builder()
                .tag(RSCPTag.TAG_DB_REQ_HISTORY_TIME_INTERVAL)
                .timestampValue(interval)
                .build();

        RSCPData reqTimeSpan = RSCPData.builder()
                .tag(RSCPTag.TAG_DB_REQ_HISTORY_TIME_SPAN)
                .timestampValue(timeSpan)
                .build();

        // build request starting with a container
        return RSCPData.builder()
                .tag(RSCPTag.TAG_DB_REQ_HISTORY_DATA_DAY)
                .containerValues(Arrays.asList(reqTimeStart, reqInterval, reqTimeSpan))
                .build();
    }

    private byte[] createLeBytes(int size, long value) {
        ByteBuffer buffer = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
        if (size == 1) {
            buffer.put((byte) value);
        } else if (size == 2) {
            buffer.putShort((short) value);
        } else if (size == 4) {
            buffer.putInt((int) value);
        } else if (size == 8) {
            buffer.putLong(value);
        }
        return buffer.array();
    }
}
