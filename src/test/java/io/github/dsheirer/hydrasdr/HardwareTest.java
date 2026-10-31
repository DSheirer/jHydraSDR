package io.github.dsheirer.hydrasdr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Receive-only tests against a real device. Enable with {@code -PhardwareTest=true}. */
@EnabledIfSystemProperty(named = "hydrasdr.hardware.test", matches = "true")
class HardwareTest {
    private static final long FREQUENCY = 100_000_000L;

    private static void configure(HydraSdrDevice device, SampleType type) throws HydraSdrException {
        device.setSampleType(type);
        long[] rates = device.sampleRates();
        device.setFrequency(FREQUENCY);
        device.setSampleRate(rates[rates.length - 1]);
    }

    @Test
    void readsDeviceInfo() throws HydraSdrException {
        long[] serials = HydraSdr.listDevices();
        assertTrue(serials.length > 0, "No device found");
        try (HydraSdrDevice device = HydraSdr.open(serials[0])) {
            DeviceInfo info = device.deviceInfo();
            assertEquals(serials[0], info.serialNumber());
            assertFalse(info.boardName().isEmpty());
            assertFalse(info.firmwareVersion().isEmpty());
            assertTrue(info.minFrequency() < info.maxFrequency());
            assertTrue(info.has(Capability.RX));
            assertTrue(device.sampleRates().length > 0);
        }
    }

    @Test
    void streamsSamplesAndStops() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            configure(device, SampleType.FLOAT32_IQ);
            CountDownLatch received = new CountDownLatch(50);
            AtomicReference<float[]> last = new AtomicReference<>();
            device.startRx(buffer -> {
                assertEquals(SampleType.FLOAT32_IQ, buffer.sampleType());
                last.set(buffer.copyFloats());
                received.countDown();
                return true;
            });
            assertTrue(received.await(10, TimeUnit.SECONDS), "Samples did not arrive");
            assertTrue(device.isStreaming());
            device.stopRx();
            assertFalse(device.isStreaming());
            assertNull(device.streamingFailure());
            float[] samples = last.get();
            assertTrue(samples.length > 0);
            boolean nonZero = false;
            for (float sample : samples) {
                assertTrue(Float.isFinite(sample));
                nonZero |= sample != 0;
            }
            assertTrue(nonZero, "Samples were all zero");
        }
    }

    @Test
    void streamsEverySupportedType() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            for (SampleType type : device.deviceInfo().sampleTypes()) {
                configure(device, type);
                CountDownLatch received = new CountDownLatch(5);
                device.startRx(buffer -> {
                    // Reading the last byte proves the computed size is usable.
                    buffer.copyBytes();
                    received.countDown();
                    return true;
                });
                assertTrue(received.await(10, TimeUnit.SECONDS), "No samples for " + type);
                device.stopRx();
                assertNull(device.streamingFailure(), type.toString());
            }
        }
    }

    @Test
    void packedRawTransfersMatchUsbBufferSize() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            assumeTrue(device.deviceInfo().has(Capability.PACKING));
            device.setPacking(true);
            configure(device, SampleType.RAW);
            CountDownLatch received = new CountDownLatch(5);
            AtomicLong bytes = new AtomicLong();
            AtomicLong samples = new AtomicLong();
            device.startRx(buffer -> {
                bytes.set(buffer.samples().byteSize());
                samples.set(buffer.sampleCount());
                received.countDown();
                return true;
            });
            assertTrue(received.await(10, TimeUnit.SECONDS));
            device.stopRx();
            assertNull(device.streamingFailure());
            assertEquals(samples.get() * 3 / 2, bytes.get());
        }
    }

    @Test
    void canRestartAfterListenerEndedStream() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            configure(device, SampleType.INT16_IQ);
            for (int run = 0; run < 3; run++) {
                CountDownLatch received = new CountDownLatch(1);
                device.startRx(buffer -> {
                    received.countDown();
                    return false;
                });
                assertTrue(received.await(10, TimeUnit.SECONDS), "No samples in run " + run);
                for (int i = 0; i < 200 && device.isStreaming(); i++) {
                    Thread.sleep(5);
                }
            }
            assertNull(device.streamingFailure());
        }
    }

    @Test
    void listenerExceptionStopsStreamInsteadOfCrashing() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            configure(device, SampleType.INT16_IQ);
            CountDownLatch called = new CountDownLatch(1);
            device.startRx(buffer -> {
                called.countDown();
                throw new IllegalArgumentException("expected");
            });
            assertTrue(called.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 100 && device.streamingFailure() == null; i++) {
                Thread.sleep(10);
            }
            assertInstanceOf(IllegalArgumentException.class, device.streamingFailure());
            device.stopRx();
        }
    }

    @Test
    void samplesCannotBeUsedAfterCallbackReturns() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            configure(device, SampleType.FLOAT32_IQ);
            AtomicReference<MemorySegment> kept = new AtomicReference<>();
            CountDownLatch called = new CountDownLatch(1);
            device.startRx(buffer -> {
                kept.set(buffer.samples());
                called.countDown();
                return false;
            });
            assertTrue(called.await(10, TimeUnit.SECONDS));
            device.stopRx();
            assertThrows(RuntimeException.class, () -> kept.get().get(ValueLayout.JAVA_BYTE, 0));
        }
    }

    @Test
    @SuppressWarnings("try")
    void stoppingFromListenerIsRejected() throws Exception {
        try (HydraSdrDevice device = HydraSdr.open()) {
            configure(device, SampleType.FLOAT32_IQ);
            AtomicReference<Throwable> thrown = new AtomicReference<>();
            CountDownLatch called = new CountDownLatch(1);
            device.startRx(buffer -> {
                try {
                    device.close();
                } catch (Throwable t) {
                    thrown.set(t);
                }
                called.countDown();
                return false;
            });
            assertTrue(called.await(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, thrown.get());
        }
    }
}
