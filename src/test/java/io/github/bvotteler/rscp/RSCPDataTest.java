package io.github.bvotteler.rscp;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
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
    public void builder__from_raw() {
        RSCPData container = buildSampleDBRequestContainer(Instant.ofEpochSecond(42L), Duration.ofSeconds(900L), Duration.ofSeconds(900L));

        // contains the inner RSCPData instances as byte array
        byte[] raw = container.getValueAsByteArray();

        List<RSCPData> fromRaw = RSCPData.builder().buildFromRawBytes(raw);

        assertThat(container.getContainerData(), equalTo(fromRaw));
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