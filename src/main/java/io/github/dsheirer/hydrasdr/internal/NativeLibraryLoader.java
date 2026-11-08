package io.github.dsheirer.hydrasdr.internal;

import io.github.dsheirer.libusb.NativeLoader;
import java.lang.foreign.SymbolLookup;
import java.util.List;

/**
 * Loads libhydrasdr through jlibusb, which supplies the libusb it depends on.
 *
 * <p>By default libhydrasdr is extracted from the jar, next to jlibusb's copy
 * of libusb. Set the {@value #PATH_PROPERTY} system property to a directory to
 * load libhydrasdr from there instead; set {@code jlibusb.library.path} to
 * replace libusb.
 */
public final class NativeLibraryLoader {
    /** System property naming a directory that overrides the bundled libhydrasdr. */
    public static final String PATH_PROPERTY = "jhydrasdr.library.path";

    private static final String RESOURCE_ROOT = "/jhydrasdr/native/";

    private NativeLibraryLoader() {
    }

    /** File name of libhydrasdr on a platform, such as {@code linux-x86_64}. */
    public static String libraryName(String platform) {
        if (platform.startsWith("linux")) {
            return "libhydrasdr.so";
        }
        if (platform.startsWith("macos")) {
            return "libhydrasdr.dylib";
        }
        return "hydrasdr.dll";
    }

    /** Loads libusb and libhydrasdr and returns a lookup for the symbols of libhydrasdr. */
    static SymbolLookup load() {
        String name = libraryName(NativeLoader.platform());
        return NativeLoader.load(NativeLibraryLoader.class, RESOURCE_ROOT, List.of(name), PATH_PROPERTY);
    }
}
