package io.github.dsheirer.hydrasdr;

import java.util.Arrays;
import java.util.Locale;

/** Command line tool that prints the library version and the connected devices. */
public final class HydraSdrInfo {
    private HydraSdrInfo() {
    }

    /** Runs the tool. */
    public static void main(String[] args) {
        System.out.println("libhydrasdr " + HydraSdr.libraryVersion());
        long[] serials;
        try {
            serials = HydraSdr.listDevices();
        } catch (HydraSdrException e) {
            // Machines without USB access, such as containers, end up here.
            System.out.println("Unable to list devices: " + e.getMessage());
            return;
        }
        System.out.println("Devices found: " + serials.length);
        for (long serial : serials) {
            System.out.println(String.format(Locale.ROOT, "Serial 0x%016X", serial));
            try (HydraSdrDevice device = HydraSdr.open(serial)) {
                DeviceInfo info = device.deviceInfo();
                System.out.println("  Board:        " + info.boardName());
                System.out.println("  Firmware:     " + info.firmwareVersion());
                System.out.println("  Frequency:    " + info.minFrequency() + " - " + info.maxFrequency() + " Hz");
                System.out.println("  Sample rates: " + Arrays.toString(device.sampleRates()));
                System.out.println("  Capabilities: " + info.capabilities());
            } catch (HydraSdrException e) {
                System.out.println("  Unable to open: " + e.getMessage());
            }
        }
    }
}
