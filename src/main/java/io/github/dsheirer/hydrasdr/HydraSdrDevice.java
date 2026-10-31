package io.github.dsheirer.hydrasdr;

import static io.github.dsheirer.hydrasdr.HydraSdr.check;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import io.github.dsheirer.hydrasdr.internal.Layouts;
import io.github.dsheirer.hydrasdr.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An open HydraSDR device. Close it when finished, which also stops streaming.
 *
 * <p>For correct automatic bandwidth selection, configure a device in this
 * order before streaming: frequency, decimation mode, bandwidth, sample rate,
 * sample type, gains.
 *
 * <p>Control methods may be called from any thread, including from a
 * {@link SampleListener}, except {@link #stopRx()} and {@link #close()}. Do not
 * close a device while other threads are still using it.
 */
public final class HydraSdrDevice implements AutoCloseable {
    /** Bandwidth value that makes the library select the bandwidth automatically. */
    public static final long BANDWIDTH_AUTO = 0xFFFF_FFFFL;

    private static final int MAX_FLASH_TRANSFER = 0xFFFF;
    private static final MethodHandle ON_TRANSFER;

    static {
        try {
            ON_TRANSFER = MethodHandles.lookup().findVirtual(HydraSdrDevice.class, "onTransfer",
                MethodType.methodType(int.class, MemorySegment.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final MemorySegment handle;
    private final Object lifecycleLock = new Object();
    private boolean closed;

    // Streaming state: written under lifecycleLock before the stream starts,
    // read by the callback thread.
    private Arena streamArena;
    private volatile SampleListener listener;
    private volatile Thread callbackThread;
    private volatile Throwable streamingFailure;
    private volatile boolean packedStream;
    private volatile int streamAdcBits;

    HydraSdrDevice(MemorySegment handle) {
        this.handle = handle;
    }

    // ---- Information ----

    /** Returns the device's capabilities and current configuration. */
    public DeviceInfo deviceInfo() throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(Layouts.DEVICE_INFO);
            check("hydrasdr_get_device_info", Native.invoke(Native.GET_DEVICE_INFO, handle(), info));
            return DeviceInfo.read(info);
        }
    }

    /** Reads the temperature sensor; see {@link Capability#TEMPERATURE_SENSOR}. */
    public Temperature temperature() throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment t = arena.allocate(Layouts.TEMPERATURE);
            check("hydrasdr_get_temperature", Native.invoke(Native.GET_TEMPERATURE, handle(), t));
            return new Temperature(Layouts.f32(t, Layouts.TEMPERATURE, "temperature_celsius"),
                Layouts.f32(t, Layouts.TEMPERATURE, "temperature_fahrenheit"),
                Layouts.u8(t, Layouts.TEMPERATURE, "valid") != 0);
        }
    }

    /** Resets the device. It has to be opened again afterwards. */
    public void reset() throws HydraSdrException {
        check("hydrasdr_reset", Native.invoke(Native.RESET, handle()));
    }

    // ---- Tuning ----

    /** Tunes to a frequency in Hz. */
    public void setFrequency(long hertz) throws HydraSdrException {
        check("hydrasdr_set_freq", Native.invoke(Native.SET_FREQ, handle(), hertz));
    }

    /** Returns the sample rates in Hz for the current sample type. */
    public long[] sampleRates() throws HydraSdrException {
        return rates("hydrasdr_get_samplerates", Native.GET_SAMPLERATES);
    }

    /** Selects a sample rate in Hz from {@link #sampleRates()}. */
    public void setSampleRate(long hertz) throws HydraSdrException {
        check("hydrasdr_set_samplerate", Native.invoke(Native.SET_SAMPLERATE, handle(), u32(hertz)));
    }

    /** Returns the supported RF bandwidths in Hz. */
    public long[] bandwidths() throws HydraSdrException {
        return rates("hydrasdr_get_bandwidths", Native.GET_BANDWIDTHS);
    }

    /** Selects an RF bandwidth in Hz, or {@link #BANDWIDTH_AUTO}. Call before {@link #setSampleRate}. */
    public void setBandwidth(long hertz) throws HydraSdrException {
        check("hydrasdr_set_bandwidth", Native.invoke(Native.SET_BANDWIDTH, handle(), u32(hertz)));
    }

    /** Selects how decimation is applied. Call before {@link #setSampleRate}. */
    public void setDecimationMode(DecimationMode mode) throws HydraSdrException {
        check("hydrasdr_set_decimation_mode",
            Native.invoke(Native.SET_DECIMATION_MODE, handle(), mode.ordinal()));
    }

    /** Returns the decimation mode. */
    public DecimationMode decimationMode() throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment mode = arena.allocate(JAVA_INT);
            check("hydrasdr_get_decimation_mode", Native.invoke(Native.GET_DECIMATION_MODE, handle(), mode));
            return DecimationMode.values()[mode.get(JAVA_INT, 0)];
        }
    }

    /** Selects the format of delivered samples. */
    public void setSampleType(SampleType type) throws HydraSdrException {
        check("hydrasdr_set_sample_type", Native.invoke(Native.SET_SAMPLE_TYPE, handle(), type.code()));
    }

    /** Enables 12-bit USB packing; see {@link Capability#PACKING}. */
    public void setPacking(boolean enabled) throws HydraSdrException {
        check("hydrasdr_set_packing", Native.invoke(Native.SET_PACKING, handle(), flag(enabled)));
    }

    /** Selects an algorithm named by {@link HydraSdr#conversionAlgorithms()}. */
    public void setConversionAlgorithm(String name) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            check("hydrasdr_set_conversion_algorithm",
                Native.invoke(Native.SET_CONVERSION_ALGORITHM, handle(), arena.allocateFrom(name)));
        }
    }

    /** Installs a custom float conversion filter kernel. */
    public void setConversionFilter(float[] kernel) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            check("hydrasdr_set_conversion_filter_float32", Native.invoke(Native.SET_CONVERSION_FILTER_FLOAT32,
                handle(), arena.allocateFrom(JAVA_FLOAT, kernel), kernel.length));
        }
    }

    /** Installs a custom 16-bit conversion filter kernel. */
    public void setConversionFilter(short[] kernel) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            check("hydrasdr_set_conversion_filter_int16", Native.invoke(Native.SET_CONVERSION_FILTER_INT16,
                handle(), arena.allocateFrom(JAVA_SHORT, kernel), kernel.length));
        }
    }

    // ---- Gain and RF ----

    /** Sets a gain, or enables an AGC with 1 and disables it with 0. */
    public void setGain(GainType type, int value) throws HydraSdrException {
        check("hydrasdr_set_gain", Native.invoke(Native.SET_GAIN, handle(), type.ordinal(), u8(value)));
    }

    /** Returns the cached state of a gain control. */
    public GainInfo gain(GainType type) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(Layouts.GAIN_INFO);
            check("hydrasdr_get_gain", Native.invoke(Native.GET_GAIN, handle(), type.ordinal(), info));
            return gainInfo(info);
        }
    }

    /** Returns the cached state of every gain control the device has. */
    public List<GainInfo> gains() throws HydraSdrException {
        int capacity = GainType.values().length;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment gains = arena.allocate(Layouts.GAIN_INFO, capacity);
            MemorySegment count = arena.allocate(JAVA_BYTE);
            count.set(JAVA_BYTE, 0, (byte) capacity);
            check("hydrasdr_get_all_gains", Native.invoke(Native.GET_ALL_GAINS, handle(), gains, count));
            int found = Math.min(Byte.toUnsignedInt(count.get(JAVA_BYTE, 0)), capacity);
            List<GainInfo> result = new ArrayList<>(found);
            long size = Layouts.GAIN_INFO.byteSize();
            for (int i = 0; i < found; i++) {
                result.add(gainInfo(gains.asSlice(i * size, size)));
            }
            return List.copyOf(result);
        }
    }

    private static GainInfo gainInfo(MemorySegment g) {
        int flags = Layouts.u8(g, Layouts.GAIN_INFO, "flags");
        GainRange range = new GainRange(Layouts.u8(g, Layouts.GAIN_INFO, "min_value"),
            Layouts.u8(g, Layouts.GAIN_INFO, "max_value"), Layouts.u8(g, Layouts.GAIN_INFO, "step_value"),
            Layouts.u8(g, Layouts.GAIN_INFO, "default_value"));
        return new GainInfo(GainType.values()[Layouts.u8(g, Layouts.GAIN_INFO, "type")],
            Layouts.u8(g, Layouts.GAIN_INFO, "value"), range, (flags & 1) != 0, (flags & 2) != 0);
    }

    /**
     * Switches the bias tee of the selected RF port. This puts DC power on the
     * antenna connector, which can damage equipment not built for it.
     */
    public void setBiasTee(boolean enabled) throws HydraSdrException {
        check("hydrasdr_set_rf_bias", Native.invoke(Native.SET_RF_BIAS, handle(), flag(enabled)));
    }

    /** Selects an RF port by its index in {@link DeviceInfo#rfPorts()}. */
    public void setRfPort(int port) throws HydraSdrException {
        check("hydrasdr_set_rf_port", Native.invoke(Native.SET_RF_PORT, handle(), port));
    }

    // ---- Streaming ----

    /**
     * Starts streaming samples to a listener.
     *
     * @throws IllegalStateException if the device is already streaming
     */
    public void startRx(SampleListener sampleListener) throws HydraSdrException {
        Objects.requireNonNull(sampleListener, "sampleListener");
        synchronized (lifecycleLock) {
            MemorySegment device = handle();
            if (isStreaming()) {
                throw new IllegalStateException("Device is already streaming");
            }
            // A listener may have ended the previous stream without stopRx.
            stopRx();

            // RAW and UINT16 buffers have a size that depends on these settings.
            CurrentConfiguration current = deviceInfo().current();
            packedStream = current.packed();
            streamAdcBits = current.adcBits();
            streamingFailure = null;
            listener = sampleListener;

            Arena arena = Arena.ofShared();
            try {
                MemorySegment callback = Native.sampleCallback(ON_TRANSFER.bindTo(this), arena);
                check("hydrasdr_start_rx", Native.invoke(Native.START_RX, device, callback, MemorySegment.NULL));
                streamArena = arena;
            } catch (HydraSdrException | RuntimeException e) {
                arena.close();
                throw e;
            }
        }
    }

    /**
     * Stops streaming. Does nothing if the device is not streaming.
     *
     * @throws IllegalStateException if called from a {@link SampleListener}
     */
    public void stopRx() throws HydraSdrException {
        rejectCallbackThread("stopRx");
        synchronized (lifecycleLock) {
            if (streamArena != null) {
                try {
                    check("hydrasdr_stop_rx", Native.invoke(Native.STOP_RX, handle()));
                } finally {
                    releaseStream();
                }
            }
        }
    }

    /** Returns true while samples are being delivered. */
    public boolean isStreaming() throws HydraSdrException {
        return check("hydrasdr_is_streaming", Native.invoke(Native.IS_STREAMING, handle())) != 0;
    }

    /** Returns the exception that stopped streaming, or null if there was none. */
    public Throwable streamingFailure() {
        return streamingFailure;
    }

    /** Returns the streaming counters. */
    public StreamingStats streamingStats() throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(Layouts.STREAMING_STATS);
            check("hydrasdr_get_streaming_stats", Native.invoke(Native.GET_STREAMING_STATS, handle(), s));
            return new StreamingStats(Layouts.i64(s, Layouts.STREAMING_STATS, "buffers_received"),
                Layouts.i64(s, Layouts.STREAMING_STATS, "buffers_processed"),
                Layouts.i64(s, Layouts.STREAMING_STATS, "buffers_dropped"));
        }
    }

    /**
     * Called by the streaming thread through the upcall stub. An exception that
     * escaped from here would terminate the JVM, so all of them are caught.
     */
    @SuppressWarnings("unused")
    private int onTransfer(MemorySegment transferPointer) {
        callbackThread = Thread.currentThread();
        // Closing this arena ends the samples segment's lifetime, so that a
        // listener that keeps it gets an exception and not freed native memory.
        try (Arena callbackArena = Arena.ofConfined()) {
            MemorySegment t = transferPointer.reinterpret(Layouts.TRANSFER.byteSize());
            int count = t.get(JAVA_INT, Layouts.offset(Layouts.TRANSFER, "sample_count"));
            SampleType type = SampleType.values()[t.get(JAVA_INT, Layouts.offset(Layouts.TRANSFER, "sample_type"))];
            long bytes = type.byteLength(count, packedStream, streamAdcBits);
            MemorySegment samples = t.get(ADDRESS,
                Layouts.offset(Layouts.TRANSFER, "samples")).reinterpret(bytes, callbackArena, null);
            long dropped = Layouts.i64(t, Layouts.TRANSFER, "dropped_samples");
            return listener.onSamples(new SampleBuffer(samples, count, type, dropped)) ? 0 : 1;
        } catch (Throwable failure) {
            streamingFailure = failure;
            return 1;
        }
    }

    private void releaseStream() {
        if (streamArena != null) {
            streamArena.close();
            streamArena = null;
        }
        listener = null;
        callbackThread = null;
    }

    private void rejectCallbackThread(String method) {
        if (Thread.currentThread() == callbackThread) {
            throw new IllegalStateException(method + " cannot be called from a SampleListener");
        }
    }

    // ---- Low-level hardware access ----

    /** Writes a GPIO pin. */
    public void gpioWrite(int port, int pin, boolean high) throws HydraSdrException {
        check("hydrasdr_gpio_write", Native.invoke(Native.GPIO_WRITE, handle(), port, pin, flag(high)));
    }

    /** Reads a GPIO pin. */
    public boolean gpioRead(int port, int pin) throws HydraSdrException {
        return readByte("hydrasdr_gpio_read", Native.GPIO_READ, port, pin) != 0;
    }

    /** Sets a GPIO pin's direction. */
    public void gpioSetOutput(int port, int pin, boolean output) throws HydraSdrException {
        check("hydrasdr_gpiodir_write", Native.invoke(Native.GPIODIR_WRITE, handle(), port, pin, flag(output)));
    }

    /** Returns true if a GPIO pin is an output. */
    public boolean gpioIsOutput(int port, int pin) throws HydraSdrException {
        return readByte("hydrasdr_gpiodir_read", Native.GPIODIR_READ, port, pin) != 0;
    }

    /** Writes a clock generator register. */
    public void clockGeneratorWrite(int register, int value) throws HydraSdrException {
        check("hydrasdr_clockgen_write", Native.invoke(Native.CLOCKGEN_WRITE, handle(), u8(register), u8(value)));
    }

    /** Reads a clock generator register. */
    public int clockGeneratorRead(int register) throws HydraSdrException {
        return readByte("hydrasdr_clockgen_read", Native.CLOCKGEN_READ, u8(register));
    }

    /** Writes an RF frontend register. */
    public void rfFrontendWrite(int register, long value) throws HydraSdrException {
        check("hydrasdr_rf_frontend_write",
            Native.invoke(Native.RF_FRONTEND_WRITE, handle(), u16(register), u32(value)));
    }

    /** Reads an RF frontend register. */
    public long rfFrontendRead(int register) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_INT);
            check("hydrasdr_rf_frontend_read",
                Native.invoke(Native.RF_FRONTEND_READ, handle(), u16(register), value));
            return Integer.toUnsignedLong(value.get(JAVA_INT, 0));
        }
    }

    /** Erases the whole SPI flash, including the firmware. */
    public void spiFlashErase() throws HydraSdrException {
        check("hydrasdr_spiflash_erase", Native.invoke(Native.SPIFLASH_ERASE, handle()));
    }

    /** Erases one SPI flash sector. */
    public void spiFlashEraseSector(int sector) throws HydraSdrException {
        check("hydrasdr_spiflash_erase_sector", Native.invoke(Native.SPIFLASH_ERASE_SECTOR, handle(), u16(sector)));
    }

    /** Writes to the SPI flash, in one transfer of at most 65535 bytes. */
    public void spiFlashWrite(long address, byte[] data) throws HydraSdrException {
        checkFlashLength(data.length);
        try (Arena arena = Arena.ofConfined()) {
            check("hydrasdr_spiflash_write", Native.invoke(Native.SPIFLASH_WRITE, handle(), u32(address),
                (short) data.length, arena.allocateFrom(JAVA_BYTE, data)));
        }
    }

    /** Reads from the SPI flash, in one transfer of at most 65535 bytes. */
    public byte[] spiFlashRead(long address, int length) throws HydraSdrException {
        checkFlashLength(length);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(length);
            check("hydrasdr_spiflash_read",
                Native.invoke(Native.SPIFLASH_READ, handle(), u32(address), (short) length, data));
            return data.toArray(JAVA_BYTE);
        }
    }

    private static void checkFlashLength(int length) {
        if (length < 0 || length > MAX_FLASH_TRANSFER) {
            throw new IllegalArgumentException("Flash transfer length out of range: " + length);
        }
    }

    // ---- Lifecycle ----

    /**
     * Stops streaming and releases the device. Closing again does nothing.
     *
     * @throws IllegalStateException if called from a {@link SampleListener}
     */
    @Override
    public void close() throws HydraSdrException {
        rejectCallbackThread("close");
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            try {
                stopRx();
            } finally {
                closed = true;
                check("hydrasdr_close", Native.invoke(Native.CLOSE, handle));
            }
        }
    }

    private MemorySegment handle() {
        if (closed) {
            throw new IllegalStateException("Device is closed");
        }
        return handle;
    }

    private long[] rates(String function, MethodHandle getter) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            // A length of 0 makes the function store the count in the first element.
            MemorySegment countSegment = arena.allocate(JAVA_INT);
            check(function, Native.invoke(getter, handle(), countSegment, 0));
            int count = countSegment.get(JAVA_INT, 0);
            MemorySegment values = arena.allocate(JAVA_INT, Math.max(count, 1));
            check(function, Native.invoke(getter, handle(), values, count));
            long[] result = new long[count];
            for (int i = 0; i < count; i++) {
                result[i] = Integer.toUnsignedLong(values.getAtIndex(JAVA_INT, i));
            }
            return result;
        }
    }

    private int readByte(String function, MethodHandle getter, Object... arguments) throws HydraSdrException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_BYTE);
            Object[] all = new Object[arguments.length + 2];
            all[0] = handle();
            System.arraycopy(arguments, 0, all, 1, arguments.length);
            all[all.length - 1] = value;
            check(function, Native.invoke(getter, all));
            return Byte.toUnsignedInt(value.get(JAVA_BYTE, 0));
        }
    }

    private static byte flag(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    private static byte u8(int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value out of 8-bit range: " + value);
        }
        return (byte) value;
    }

    private static short u16(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of 16-bit range: " + value);
        }
        return (short) value;
    }

    private static int u32(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Value out of 32-bit range: " + value);
        }
        return (int) value;
    }
}
