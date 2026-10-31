package io.github.dsheirer.hydrasdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.hydrasdr.internal.Layouts;
import io.github.dsheirer.hydrasdr.internal.Native;
import io.github.dsheirer.hydrasdr.internal.NativeLibraryLoader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests that need the native library for the current platform but no hardware. */
class LibraryTest {
    @Test
    void structureSizesMatchHeader() {
        assertEquals(4096, Layouts.DEVICE_INFO.byteSize());
        assertEquals(64, Layouts.COMPONENT_INFO.byteSize());
        assertEquals(56, Layouts.RF_PORT_INFO.byteSize());
        assertEquals(8, Layouts.GAIN_INFO.byteSize());
        assertEquals(8, Layouts.GAIN_RANGE.byteSize());
        assertEquals(16, Layouts.TEMPERATURE.byteSize());
        assertEquals(64, Layouts.STREAMING_STATS.byteSize());
        assertEquals(48, Layouts.TRANSFER.byteSize());
    }

    @Test
    void loadsLibraryAndResolvesEveryFunction() {
        LibraryVersion version = HydraSdr.libraryVersion();
        assertTrue(version.major() >= 1, version.toString());
        assertNotNull(Native.GET_DEVICE_INFO);
    }

    @Test
    void translatesNativeErrorNames() {
        assertEquals("HYDRASDR_ERROR_BUSY", Native.errorName(HydraSdrError.BUSY.code()));
        assertEquals(HydraSdrError.BUSY, HydraSdrError.fromCode(-6));
        assertEquals(HydraSdrError.OTHER, HydraSdrError.fromCode(-12345));
    }

    @Test
    void listsConversionAlgorithms() throws HydraSdrException {
        List<ConversionAlgorithm> algorithms = HydraSdr.conversionAlgorithms();
        assertFalse(algorithms.isEmpty());
        assertTrue(algorithms.stream().anyMatch(a -> a.name().equals("47_opt")), algorithms.toString());
    }

    @Test
    void enumeratesWithoutFailing() {
        // CI machines may have no USB bus, which the library reports as one of these.
        try {
            assertNotNull(HydraSdr.listDevices());
        } catch (HydraSdrException e) {
            assertTrue(Set.of(HydraSdrError.LIBUSB, HydraSdrError.NOT_FOUND).contains(e.error()), e.getMessage());
        }
    }

    @Test
    void capabilitiesDecodeFromBits() {
        assertEquals(Set.of(Capability.LNA_GAIN, Capability.BIAS_TEE, Capability.EXTENDED_GAIN),
            Capability.fromMask(1L | 1L << 11 | 1L << 22));
    }

    @Test
    void transferSizesFollowStreamFormat() {
        assertEquals(800, SampleType.FLOAT32_IQ.byteLength(100, false, 12));
        assertEquals(400, SampleType.INT16_IQ.byteLength(100, false, 12));
        assertEquals(200, SampleType.UINT8_IQ.byteLength(100, false, 8));
        assertEquals(100, SampleType.INT8_REAL.byteLength(100, false, 12));
        assertEquals(200, SampleType.RAW.byteLength(100, false, 12));
        assertEquals(150, SampleType.RAW.byteLength(100, true, 12));
        assertEquals(100, SampleType.RAW.byteLength(100, false, 8));
        assertEquals(200, SampleType.UINT16_REAL.byteLength(100, true, 12));
    }

    @Test
    void mapsPlatforms() {
        assertEquals("linux-x86_64", NativeLibraryLoader.platform("Linux", "amd64"));
        assertEquals("linux-aarch64", NativeLibraryLoader.platform("Linux", "aarch64"));
        assertEquals("macos-x86_64", NativeLibraryLoader.platform("Mac OS X", "x86_64"));
        assertEquals("macos-aarch64", NativeLibraryLoader.platform("Mac OS X", "aarch64"));
        assertEquals("windows-x86_64", NativeLibraryLoader.platform("Windows 11", "amd64"));
        assertEquals("windows-aarch64", NativeLibraryLoader.platform("Windows 11", "aarch64"));
        assertThrows(UnsatisfiedLinkError.class, () -> NativeLibraryLoader.platform("Solaris", "amd64"));
        assertThrows(UnsatisfiedLinkError.class, () -> NativeLibraryLoader.platform("Linux", "riscv64"));
    }
}
