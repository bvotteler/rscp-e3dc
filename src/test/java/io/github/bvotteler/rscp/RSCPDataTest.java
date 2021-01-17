package io.github.bvotteler.rscp;

import io.github.bvotteler.rscp.util.ByteUtils;
import org.hamcrest.CoreMatchers;
import org.junit.Test;

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
        RSCPData noneData2 = RSCPData.builder().tag(RSCPTag.TAG_EMS_REQ_POWER_PV).nullValue().build();

        assertThat(noneData1.getDataType(), equalTo(RSCPDataType.NONE));
        assertThat(noneData2.getDataType(), equalTo(RSCPDataType.NONE));

        assertThat(noneData1.getValueAsByteArray().length, equalTo(0));
        assertThat(noneData1, equalTo(noneData2));
    }

    @Test
    public void none__can_be_serialized_and_deserialized() {
        RSCPData noneData = RSCPData.builder().tag(RSCPTag.TAG_EMS_REQ_POWER_PV).nullValue().build();
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
}