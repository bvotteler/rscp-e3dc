package io.github.bvotteler.rscp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum RSCPDataType {
    NONE((byte) 0x00),
    BOOL((byte) 0x01),
    CHAR8((byte) 0x02),
    UCHAR8((byte) 0x03),
    INT16((byte) 0x04),
    UINT16((byte) 0x05),
    INT32((byte) 0x06),
    UINT32((byte) 0x07),
    INT64((byte) 0x08),
    UINT64((byte) 0x09),
    FLOAT32((byte) 0x0A),
    DOUBLE64((byte) 0x0B),
    BITFIELD((byte) 0x0C),
    STRING((byte) 0x0D),
    CONTAINER((byte) 0x0E),
    TIMESTAMP((byte) 0x0F),
    BYTEARRAY((byte) 0x10),
    ERROR((byte) 0xFF);

    private static final Map<Byte, RSCPDataType> BYTE_TO_DATA_TYPE = new HashMap<>();

    private static final Set<RSCPDataType> VALID_SHORT_TYPES = Stream.of(
            CHAR8,
            UCHAR8,
            INT16,
            UINT16
    ).collect(Collectors.toSet());

    private static final Set<RSCPDataType> VALID_INT_TYPES = Stream.concat(
            VALID_SHORT_TYPES.stream(), Stream.of(INT32, UINT32)
    ).collect(Collectors.toSet());

    private static final Set<RSCPDataType> VALID_LONG_TYPES = Stream.concat(
            VALID_INT_TYPES.stream(), Stream.of(INT64, UINT64)
    ).collect(Collectors.toSet());

    static {
        for (RSCPDataType dataType : values()) {
            BYTE_TO_DATA_TYPE.put(dataType.getValue(), dataType);
        }
    }

    private final byte id;

    RSCPDataType(byte id) {
        this.id = id;
    }

    public static RSCPDataType getDataTypeForBytes(byte which) {
        return BYTE_TO_DATA_TYPE.get(which);
    }

    public byte getValue() {
        return this.id;
    }

    public boolean equals(byte value) {
        return this.id == value;
    }

    public boolean isValidShortType() {
        return VALID_SHORT_TYPES.contains(this);
    }

    public boolean isValidIntType() {
        return VALID_INT_TYPES.contains(this);
    }

    public boolean isValidLongType() {
        return VALID_LONG_TYPES.contains(this);
    }
}
