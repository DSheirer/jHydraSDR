package io.github.dsheirer.hydrasdr;

/** Sample formats of {@code enum hydrasdr_sample_type}, in native order. */
public enum SampleType {
    /** 32-bit float I/Q pairs. */
    FLOAT32_IQ,
    /** 32-bit float real samples. */
    FLOAT32_REAL,
    /** Signed 16-bit I/Q pairs. */
    INT16_IQ,
    /** Signed 16-bit real samples. */
    INT16_REAL,
    /** Unsigned 16-bit real samples. */
    UINT16_REAL,
    /** ADC samples exactly as the hardware delivered them. */
    RAW,
    /** Signed 8-bit I/Q pairs. */
    INT8_IQ,
    /** Unsigned 8-bit I/Q pairs. */
    UINT8_IQ,
    /** Signed 8-bit real samples. */
    INT8_REAL,
    /** Unsigned 8-bit real samples. */
    UINT8_REAL;

    /** Returns the native enum value. */
    public int code() {
        return ordinal();
    }

    /**
     * Returns the size in bytes of a transfer of this type. The number of bytes
     * per sample of {@link #UINT16_REAL} and {@link #RAW} depends on the stream
     * because the library passes the USB buffer through without converting it.
     *
     * @param sampleCount number of samples, or of I/Q pairs for the IQ types
     * @param packed whether 12-bit packing is enabled
     * @param adcBits ADC bit depth of the stream
     */
    long byteLength(long sampleCount, boolean packed, int adcBits) {
        return switch (this) {
            case FLOAT32_IQ -> sampleCount * 8;
            case FLOAT32_REAL, INT16_IQ -> sampleCount * 4;
            case INT16_REAL, INT8_IQ, UINT8_IQ -> sampleCount * 2;
            case INT8_REAL, UINT8_REAL -> sampleCount;
            case UINT16_REAL, RAW -> {
                if (packed) {
                    // Packing is unpacked for every type except RAW.
                    yield this == RAW ? sampleCount * 3 / 2 : sampleCount * 2;
                }
                yield adcBits == 8 ? sampleCount : sampleCount * 2;
            }
        };
    }
}
