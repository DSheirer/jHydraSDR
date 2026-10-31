package io.github.dsheirer.hydrasdr;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.dsheirer.hydrasdr.internal.Layouts;
import io.github.dsheirer.hydrasdr.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

/**
 * Entry point of the library: finds and opens HydraSDR devices.
 *
 * <pre>{@code
 * try (HydraSdrDevice device = HydraSdr.open()) {
 *     device.setFrequency(100_000_000L);
 *     device.setSampleRate(device.sampleRates()[0]);
 *     device.setSampleType(SampleType.FLOAT32_IQ);
 *     device.startRx(buffer -> {
 *         process(buffer.copyFloats());
 *         return true;
 *     });
 *     Thread.sleep(1000);
 * }
 * }</pre>
 *
 * <p>The native enumeration and open functions are not thread-safe, so the
 * methods of this class run one at a time.
 */
public final class HydraSdr {
    private static final Object LOCK = new Object();

    private HydraSdr() {
    }

    /** Returns the version of the loaded native library. */
    public static LibraryVersion libraryVersion() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = arena.allocate(Layouts.LIB_VERSION);
            Native.invokeVoid(Native.LIB_VERSION, version);
            return new LibraryVersion(Layouts.u32(version, Layouts.LIB_VERSION, "major_version"),
                Layouts.u32(version, Layouts.LIB_VERSION, "minor_version"),
                Layouts.u32(version, Layouts.LIB_VERSION, "revision"));
        }
    }

    /** Returns the serial numbers of the connected devices. */
    public static long[] listDevices() throws HydraSdrException {
        synchronized (LOCK) {
            int count = check("hydrasdr_list_devices",
                Native.invoke(Native.LIST_DEVICES, MemorySegment.NULL, 0));
            if (count == 0) {
                return new long[0];
            }
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment serials = arena.allocate(JAVA_LONG, count);
                int found = check("hydrasdr_list_devices", Native.invoke(Native.LIST_DEVICES, serials, count));
                return serials.asSlice(0, Math.min(found, count) * JAVA_LONG.byteSize()).toArray(JAVA_LONG);
            }
        }
    }

    /** Opens the first available device. */
    public static HydraSdrDevice open() throws HydraSdrException {
        return open("hydrasdr_open", Native.OPEN);
    }

    /** Opens the device with a serial number from {@link #listDevices()}. */
    public static HydraSdrDevice open(long serialNumber) throws HydraSdrException {
        return open("hydrasdr_open_sn", Native.OPEN_SN, serialNumber);
    }

    /**
     * Opens a device from a USB file descriptor, as on Android. The descriptor
     * must stay open until the device is closed.
     */
    public static HydraSdrDevice openFileDescriptor(int fd) throws HydraSdrException {
        return open("hydrasdr_open_fd", Native.OPEN_FD, fd);
    }

    private static HydraSdrDevice open(String function, MethodHandle handle, Object... arguments)
            throws HydraSdrException {
        synchronized (LOCK) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pointer = arena.allocate(ADDRESS);
                Object[] all = new Object[arguments.length + 1];
                all[0] = pointer;
                System.arraycopy(arguments, 0, all, 1, arguments.length);
                check(function, Native.invoke(handle, all));
                return new HydraSdrDevice(pointer.get(ADDRESS, 0));
            }
        }
    }

    /** Returns the conversion algorithms the library provides. */
    public static List<ConversionAlgorithm> conversionAlgorithms() throws HydraSdrException {
        int count = check("hydrasdr_list_conversion_algorithms", Native.invoke(Native.LIST_CONVERSION_ALGORITHMS,
            MemorySegment.NULL, MemorySegment.NULL, 0));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment names = arena.allocate(ADDRESS, Math.max(count, 1));
            MemorySegment descriptions = arena.allocate(ADDRESS, Math.max(count, 1));
            count = Math.min(count, check("hydrasdr_list_conversion_algorithms",
                Native.invoke(Native.LIST_CONVERSION_ALGORITHMS, names, descriptions, count)));
            List<ConversionAlgorithm> algorithms = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                algorithms.add(new ConversionAlgorithm(
                    Native.cString(names.getAtIndex(ADDRESS, i)),
                    Native.cString(descriptions.getAtIndex(ADDRESS, i))));
            }
            return List.copyOf(algorithms);
        }
    }

    static int check(String function, int result) throws HydraSdrException {
        if (result < 0) {
            throw new HydraSdrException(function, result);
        }
        return result;
    }
}
