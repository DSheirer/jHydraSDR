package io.github.dsheirer.hydrasdr.internal;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Downcall handles for the public functions of libhydrasdr.
 *
 * <p>Deprecated upstream functions are intentionally absent: each has a
 * replacement here. {@code native/check-bindings.sh} compares this list against
 * {@code hydrasdr.h}.
 */
public final class Native {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = NativeLibraryLoader.load();

    public static final MethodHandle LIB_VERSION = voidCall("hydrasdr_lib_version", ADDRESS);
    public static final MethodHandle LIST_DEVICES = call("hydrasdr_list_devices", ADDRESS, JAVA_INT);
    public static final MethodHandle OPEN = call("hydrasdr_open", ADDRESS);
    public static final MethodHandle OPEN_SN = call("hydrasdr_open_sn", ADDRESS, JAVA_LONG);
    public static final MethodHandle OPEN_FD = call("hydrasdr_open_fd", ADDRESS, JAVA_INT);
    public static final MethodHandle CLOSE = call("hydrasdr_close", ADDRESS);
    public static final MethodHandle RESET = call("hydrasdr_reset", ADDRESS);
    public static final MethodHandle GET_DEVICE_INFO = call("hydrasdr_get_device_info", ADDRESS, ADDRESS);
    public static final MethodHandle GET_TEMPERATURE = call("hydrasdr_get_temperature", ADDRESS, ADDRESS);
    public static final MethodHandle ERROR_NAME = LINKER.downcallHandle(symbol("hydrasdr_error_name"),
        FunctionDescriptor.of(ADDRESS, JAVA_INT));

    public static final MethodHandle GET_SAMPLERATES = call("hydrasdr_get_samplerates", ADDRESS, ADDRESS, JAVA_INT);
    public static final MethodHandle SET_SAMPLERATE = call("hydrasdr_set_samplerate", ADDRESS, JAVA_INT);
    public static final MethodHandle GET_BANDWIDTHS = call("hydrasdr_get_bandwidths", ADDRESS, ADDRESS, JAVA_INT);
    public static final MethodHandle SET_BANDWIDTH = call("hydrasdr_set_bandwidth", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_DECIMATION_MODE = call("hydrasdr_set_decimation_mode", ADDRESS, JAVA_INT);
    public static final MethodHandle GET_DECIMATION_MODE = call("hydrasdr_get_decimation_mode", ADDRESS, ADDRESS);
    public static final MethodHandle LIST_CONVERSION_ALGORITHMS =
        call("hydrasdr_list_conversion_algorithms", ADDRESS, ADDRESS, JAVA_INT);
    public static final MethodHandle SET_CONVERSION_ALGORITHM =
        call("hydrasdr_set_conversion_algorithm", ADDRESS, ADDRESS);
    public static final MethodHandle SET_CONVERSION_FILTER_FLOAT32 =
        call("hydrasdr_set_conversion_filter_float32", ADDRESS, ADDRESS, JAVA_INT);
    public static final MethodHandle SET_CONVERSION_FILTER_INT16 =
        call("hydrasdr_set_conversion_filter_int16", ADDRESS, ADDRESS, JAVA_INT);
    public static final MethodHandle SET_SAMPLE_TYPE = call("hydrasdr_set_sample_type", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_FREQ = call("hydrasdr_set_freq", ADDRESS, JAVA_LONG);
    public static final MethodHandle SET_PACKING = call("hydrasdr_set_packing", ADDRESS, JAVA_BYTE);

    public static final MethodHandle START_RX = call("hydrasdr_start_rx", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle STOP_RX = call("hydrasdr_stop_rx", ADDRESS);
    public static final MethodHandle IS_STREAMING = call("hydrasdr_is_streaming", ADDRESS);
    public static final MethodHandle GET_STREAMING_STATS = call("hydrasdr_get_streaming_stats", ADDRESS, ADDRESS);

    public static final MethodHandle GET_GAIN = call("hydrasdr_get_gain", ADDRESS, JAVA_INT, ADDRESS);
    public static final MethodHandle SET_GAIN = call("hydrasdr_set_gain", ADDRESS, JAVA_INT, JAVA_BYTE);
    public static final MethodHandle GET_ALL_GAINS = call("hydrasdr_get_all_gains", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle SET_RF_BIAS = call("hydrasdr_set_rf_bias", ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_RF_PORT = call("hydrasdr_set_rf_port", ADDRESS, JAVA_INT);

    public static final MethodHandle GPIO_WRITE = call("hydrasdr_gpio_write", ADDRESS, JAVA_INT, JAVA_INT, JAVA_BYTE);
    public static final MethodHandle GPIO_READ = call("hydrasdr_gpio_read", ADDRESS, JAVA_INT, JAVA_INT, ADDRESS);
    public static final MethodHandle GPIODIR_WRITE =
        call("hydrasdr_gpiodir_write", ADDRESS, JAVA_INT, JAVA_INT, JAVA_BYTE);
    public static final MethodHandle GPIODIR_READ = call("hydrasdr_gpiodir_read", ADDRESS, JAVA_INT, JAVA_INT, ADDRESS);

    public static final MethodHandle CLOCKGEN_WRITE = call("hydrasdr_clockgen_write", ADDRESS, JAVA_BYTE, JAVA_BYTE);
    public static final MethodHandle CLOCKGEN_READ = call("hydrasdr_clockgen_read", ADDRESS, JAVA_BYTE, ADDRESS);
    public static final MethodHandle RF_FRONTEND_WRITE =
        call("hydrasdr_rf_frontend_write", ADDRESS, JAVA_SHORT, JAVA_INT);
    public static final MethodHandle RF_FRONTEND_READ =
        call("hydrasdr_rf_frontend_read", ADDRESS, JAVA_SHORT, ADDRESS);

    public static final MethodHandle SPIFLASH_ERASE = call("hydrasdr_spiflash_erase", ADDRESS);
    public static final MethodHandle SPIFLASH_ERASE_SECTOR = call("hydrasdr_spiflash_erase_sector", ADDRESS, JAVA_SHORT);
    public static final MethodHandle SPIFLASH_WRITE =
        call("hydrasdr_spiflash_write", ADDRESS, JAVA_INT, JAVA_SHORT, ADDRESS);
    public static final MethodHandle SPIFLASH_READ =
        call("hydrasdr_spiflash_read", ADDRESS, JAVA_INT, JAVA_SHORT, ADDRESS);

    private Native() {
    }

    private static MemorySegment symbol(String name) {
        return LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError("libhydrasdr has no symbol " + name));
    }

    private static MethodHandle call(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.of(JAVA_INT, arguments));
    }

    private static MethodHandle voidCall(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.ofVoid(arguments));
    }

    /**
     * Calls a native function returning {@code int}. This boxes its arguments,
     * which is fine for the control paths using it; samples arrive by upcall.
     */
    public static int invoke(MethodHandle function, Object... arguments) {
        try {
            return (int) function.invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
    }

    /** Calls a native function that returns nothing. */
    public static void invokeVoid(MethodHandle function, Object... arguments) {
        try {
            function.invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
    }

    /** Returns the C name of an error code, such as {@code HYDRASDR_ERROR_BUSY}. */
    public static String errorName(int code) {
        try {
            MemorySegment name = (MemorySegment) ERROR_NAME.invokeExact(code);
            return cString(name);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
    }

    /** Reads a NUL-terminated string that the library owns. */
    public static String cString(MemorySegment pointer) {
        return pointer.equals(MemorySegment.NULL) ? null : pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }

    /** Creates an upcall stub for {@code hydrasdr_sample_block_cb_fn}. */
    public static MemorySegment sampleCallback(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, FunctionDescriptor.of(JAVA_INT, ADDRESS), arena);
    }
}
