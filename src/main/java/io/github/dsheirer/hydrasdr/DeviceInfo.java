package io.github.dsheirer.hydrasdr;

import static io.github.dsheirer.hydrasdr.internal.Layouts.DEVICE_INFO;
import static io.github.dsheirer.hydrasdr.internal.Layouts.f32;
import static io.github.dsheirer.hydrasdr.internal.Layouts.i64;
import static io.github.dsheirer.hydrasdr.internal.Layouts.offset;
import static io.github.dsheirer.hydrasdr.internal.Layouts.slice;
import static io.github.dsheirer.hydrasdr.internal.Layouts.string;
import static io.github.dsheirer.hydrasdr.internal.Layouts.u16;
import static io.github.dsheirer.hydrasdr.internal.Layouts.u32;
import static io.github.dsheirer.hydrasdr.internal.Layouts.u8;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.dsheirer.hydrasdr.internal.Layouts;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Capabilities and state of an open device.
 *
 * @param boardId {@code enum hydrasdr_board_id} value
 * @param partId MCU part identifier
 * @param serialNumber serial number, the value accepted by {@link HydraSdr#open(long)}
 * @param capabilities features the device supports
 * @param gainRanges limits of the gain controls the device supports
 * @param minFrequency lowest tunable frequency in Hz
 * @param maxFrequency highest tunable frequency in Hz
 * @param typicalPowerMilliwatts typical power consumption
 * @param maxPowerMilliwatts maximum power consumption
 * @param maxSafeTemperatureCelsius highest safe PCB temperature
 */
public record DeviceInfo(int boardId, String boardName, String firmwareVersion, long partId, long serialNumber,
                         Set<Capability> capabilities, Map<GainType, GainRange> gainRanges,
                         List<ComponentInfo> components, long minFrequency, long maxFrequency,
                         List<RfPortInfo> rfPorts, int gpioCount, Set<SampleType> sampleTypes,
                         float typicalPowerMilliwatts, float maxPowerMilliwatts, float maxSafeTemperatureCelsius,
                         CurrentConfiguration current) {

    /** Returns true if the device supports a capability. */
    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    static DeviceInfo read(MemorySegment s) {
        MemorySegment partSerial = slice(s, DEVICE_INFO, "part_serial");
        long partId = word(partSerial, "part_id", 0) << 32 | word(partSerial, "part_id", 1);
        long serial = word(partSerial, "serial_no", 2) << 32 | word(partSerial, "serial_no", 3);
        Set<Capability> capabilities = Capability.fromMask(u32(s, DEVICE_INFO, "features"));

        Map<GainType, GainRange> gains = new EnumMap<>(GainType.class);
        gain(s, capabilities, Capability.LNA_GAIN, GainType.LNA, "lna_gain", gains);
        gain(s, capabilities, Capability.RF_GAIN, GainType.RF, "rf_gain", gains);
        gain(s, capabilities, Capability.MIXER_GAIN, GainType.MIXER, "mixer_gain", gains);
        gain(s, capabilities, Capability.FILTER_GAIN, GainType.FILTER, "filter_gain", gains);
        gain(s, capabilities, Capability.VGA_GAIN, GainType.VGA, "vga_gain", gains);
        gain(s, capabilities, Capability.LINEARITY_GAIN, GainType.LINEARITY, "linearity_gain", gains);
        gain(s, capabilities, Capability.SENSITIVITY_GAIN, GainType.SENSITIVITY, "sensitivity_gain", gains);

        List<ComponentInfo> components = new ArrayList<>();
        int componentCount = Math.min(u8(s, DEVICE_INFO, "component_count"), Layouts.MAX_COMPONENTS);
        for (int i = 0; i < componentCount; i++) {
            MemorySegment c = element(s, "components", i, Layouts.COMPONENT_INFO.byteSize());
            components.add(new ComponentInfo(u8(c, Layouts.COMPONENT_INFO, "type"),
                string(c, Layouts.COMPONENT_INFO, "name"), u32(c, Layouts.COMPONENT_INFO, "register_count")));
        }

        List<RfPortInfo> ports = new ArrayList<>();
        int portCount = Math.min(u8(s, DEVICE_INFO, "rf_port_count"), Layouts.MAX_RF_PORTS);
        for (int i = 0; i < portCount; i++) {
            MemorySegment p = element(s, "rf_port_info", i, Layouts.RF_PORT_INFO.byteSize());
            ports.add(new RfPortInfo(string(p, Layouts.RF_PORT_INFO, "name"),
                i64(p, Layouts.RF_PORT_INFO, "min_frequency"), i64(p, Layouts.RF_PORT_INFO, "max_frequency"),
                u8(p, Layouts.RF_PORT_INFO, "has_bias_tee") != 0,
                f32(p, Layouts.RF_PORT_INFO, "bias_tee_voltage"),
                f32(p, Layouts.RF_PORT_INFO, "bias_tee_max_current_milliamp")));
        }

        Set<SampleType> sampleTypes = EnumSet.noneOf(SampleType.class);
        int sampleTypeMask = u16(s, DEVICE_INFO, "sample_types");
        for (SampleType type : SampleType.values()) {
            if ((sampleTypeMask & (1 << type.code())) != 0) {
                sampleTypes.add(type);
            }
        }

        CurrentConfiguration current = new CurrentConfiguration(
            DecimationMode.values()[u8(s, DEVICE_INFO, "current_decimation_mode") & 1],
            u8(s, DEVICE_INFO, "current_packing") != 0,
            u8(s, DEVICE_INFO, "bandwidth_auto_selected") != 0,
            u8(s, DEVICE_INFO, "current_adc_bits"),
            u32(s, DEVICE_INFO, "current_decimation_factor"),
            u32(s, DEVICE_INFO, "current_bandwidth"),
            u32(s, DEVICE_INFO, "current_samplerate"),
            u32(s, DEVICE_INFO, "current_hw_samplerate"),
            sampleType(u32(s, DEVICE_INFO, "current_sample_type")));

        return new DeviceInfo(u8(s, DEVICE_INFO, "board_id"), string(s, DEVICE_INFO, "board_name"),
            string(s, DEVICE_INFO, "firmware_version"), partId, serial, Collections.unmodifiableSet(capabilities),
            Collections.unmodifiableMap(gains), List.copyOf(components),
            i64(s, DEVICE_INFO, "min_frequency"), i64(s, DEVICE_INFO, "max_frequency"), List.copyOf(ports),
            u16(s, DEVICE_INFO, "gpio_count"), Collections.unmodifiableSet(sampleTypes),
            f32(s, DEVICE_INFO, "typical_power_mw"), f32(s, DEVICE_INFO, "max_power_mw"),
            f32(s, DEVICE_INFO, "max_safe_temp_celsius"), current);
    }

    private static SampleType sampleType(long code) {
        return code < SampleType.values().length ? SampleType.values()[(int) code] : null;
    }

    private static long word(MemorySegment partSerial, String field, int index) {
        long offset = offset(Layouts.PART_SERIAL, field) + index * JAVA_INT.byteSize();
        return Integer.toUnsignedLong(partSerial.get(JAVA_INT, offset));
    }

    private static MemorySegment element(MemorySegment info, String array, int index, long size) {
        return info.asSlice(offset(DEVICE_INFO, array) + index * size, size);
    }

    private static void gain(MemorySegment info, Set<Capability> capabilities, Capability capability, GainType type,
                             String field, Map<GainType, GainRange> gains) {
        if (capabilities.contains(capability)) {
            MemorySegment range = slice(info, DEVICE_INFO, field);
            gains.put(type, new GainRange(u8(range, Layouts.GAIN_RANGE, "min_value"),
                u8(range, Layouts.GAIN_RANGE, "max_value"), u8(range, Layouts.GAIN_RANGE, "step_value"),
                u8(range, Layouts.GAIN_RANGE, "default_value")));
        }
    }
}
