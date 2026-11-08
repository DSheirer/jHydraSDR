package io.github.dsheirer.hydrasdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.hydrasdr.internal.Layouts;
import io.github.dsheirer.hydrasdr.internal.Native;
import io.github.dsheirer.hydrasdr.internal.NativeLibraryLoader;
import io.github.dsheirer.libusb.NativeLoader;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void namesLibraries() {
        assertEquals("libhydrasdr.so", NativeLibraryLoader.libraryName("linux-aarch64"));
        assertEquals("libhydrasdr.dylib", NativeLibraryLoader.libraryName("macos-x86_64"));
        assertEquals("hydrasdr.dll", NativeLibraryLoader.libraryName("windows-aarch64"));
    }

    @Test
    void sharesLibusbWithJlibusb() throws Throwable {
        // Loading libhydrasdr loads jlibusb's libusb, so both sit in its directory.
        assertNotNull(HydraSdr.libraryVersion());
        Path directory = NativeLoader.directory();
        String platform = NativeLoader.platform();
        assertTrue(Files.isRegularFile(directory.resolve(NativeLoader.libusbName(platform))));
        assertTrue(Files.isRegularFile(directory.resolve(NativeLibraryLoader.libraryName(platform))));
        if (platform.startsWith("linux")) {
            // One copy of libusb in the process, the one jlibusb extracted.
            List<String> mapped = Files.readAllLines(Path.of("/proc/self/maps")).stream()
                .filter(line -> line.contains("libusb-1.0"))
                .map(line -> line.substring(line.indexOf('/')))
                .distinct()
                .toList();
            assertEquals(List.of(directory.resolve("libusb-1.0.so.0").toString()), mapped);
        } else if (platform.startsWith("macos")) {
            // dyld must reuse the libusb jlibusb loaded when libhydrasdr asks for
            // @loader_path/libusb-1.0.0.dylib, even through the /var symlink.
            List<String> images = dyldImages().stream().filter(name -> name.contains("libusb-1.0")).toList();
            assertEquals(1, images.size(), images.toString());
        }
    }

    /** Paths of the images dyld has loaded into this process. */
    private static List<String> dyldImages() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup system = linker.defaultLookup();
        MethodHandle count = linker.downcallHandle(system.find("_dyld_image_count").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        MethodHandle name = linker.downcallHandle(system.find("_dyld_get_image_name").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        int images = (int) count.invokeExact();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < images; i++) {
            MemorySegment pointer = (MemorySegment) name.invokeExact(i);
            if (!pointer.equals(MemorySegment.NULL)) {
                names.add(pointer.reinterpret(Long.MAX_VALUE).getString(0));
            }
        }
        return names;
    }
}
