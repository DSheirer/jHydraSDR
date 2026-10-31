package io.github.dsheirer.hydrasdr;

import java.util.EnumSet;
import java.util.Set;

/** Feature flags of {@code hydrasdr_capability_t}, in native bit order. */
public enum Capability {
    LNA_GAIN, RF_GAIN, MIXER_GAIN, FILTER_GAIN, VGA_GAIN,
    LNA_AGC, RF_AGC, MIXER_AGC, FILTER_AGC,
    LINEARITY_GAIN, SENSITIVITY_GAIN,
    BIAS_TEE, PACKING, RF_PORT_SELECT, GPIO, SPIFLASH, CLOCKGEN, RF_FRONTEND,
    BANDWIDTH, TEMPERATURE_SENSOR, RX, EXTENDED_SAMPLERATES, EXTENDED_GAIN;

    static Set<Capability> fromMask(long mask) {
        Set<Capability> capabilities = EnumSet.noneOf(Capability.class);
        for (Capability capability : values()) {
            if ((mask & (1L << capability.ordinal())) != 0) {
                capabilities.add(capability);
            }
        }
        return capabilities;
    }
}
