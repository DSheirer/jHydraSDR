# jHydraSDR

Java bindings for [libhydrasdr](https://github.com/hydrasdr/hydrasdr-host), the
driver for HydraSDR software-defined radios, built on the Java Foreign Function
& Memory API. The jar contains the native libraries, so there is nothing else to
install.

| OS | x86-64 | aarch64 |
|---------|----|----|
| Linux (glibc 2.34+, such as Ubuntu 22.04, Debian 12, RHEL 9) | ✓ | ✓ |
| macOS 11+ | ✓ | ✓ |
| Windows | ✓ | ✓ |

Requires Java 22 or later.

## Getting it

Download `jhydrasdr-<version>.jar` from
[Releases](https://github.com/DSheirer/jHydraSDR/releases). The rolling
[`nightly`](https://github.com/DSheirer/jHydraSDR/releases/tag/nightly)
pre-release tracks `main` of this project and of upstream.

Check that your machine can see a device:

```
java -jar jhydrasdr-<version>.jar
```

## Using it

```java
try (HydraSdrDevice device = HydraSdr.open()) {
    device.setFrequency(100_000_000L);
    device.setSampleRate(2_500_000);
    device.setSampleType(SampleType.FLOAT32_IQ);
    device.setGain(GainType.LINEARITY, 10);

    device.startRx(buffer -> {
        float[] iq = buffer.copyFloats();   // interleaved I, Q
        queue.offer(iq);
        return true;                        // false stops the stream
    });
    Thread.sleep(10_000);
}
```

Use `deviceInfo()` to discover what a device supports, and configure in the
order frequency, decimation mode, bandwidth, sample rate, sample type, gains:
upstream requires that order for automatic bandwidth selection.

`SampleListener` runs on the driver's high-priority thread, so hand slow work to
another thread. `buffer.samples()` gives zero-copy access, valid only until the
listener returns; later use throws an exception rather than reading freed
memory. An exception thrown by a listener stops the stream and is available from
`streamingFailure()`.

### Native access

Java prints a warning, which future versions will turn into an error, unless
you allow native access:

```
java --enable-native-access=ALL-UNNAMED -cp app.jar:jhydrasdr.jar my.App
```

On the module path, use `--enable-native-access=io.github.dsheirer.hydrasdr`. In
your own executable jar, add `Enable-Native-Access: ALL-UNNAMED` to the manifest.

### USB access

- **Linux:** install upstream's
  [udev rules](https://github.com/hydrasdr/hydrasdr-host/blob/main/hydrasdr-tools/51-hydrasdr.rules)
  to use a device without root.
- **Windows:** the device needs the WinUSB driver.

### Native library location

Libraries are extracted to the temporary directory at startup. Set
`-Djhydrasdr.library.path=<dir>` to load them from a directory instead: for
example, to supply your own build of libusb, or if the temporary directory is
mounted `noexec`.

## Building

You need JDK 22+, CMake, a C compiler, make and git. On Windows use Visual Studio
and 7-Zip.

```
./native/build-unix.sh                                # Linux and macOS
pwsh native/build-windows.ps1 -Arch x64               # Windows; or -Arch ARM64
./gradlew build                                       # jar in build/libs
./gradlew test -PhardwareTest=true                    # also test a connected device
```

A local jar contains only your platform. The hardware tests are receive-only.

## Releases

jHydraSDR versions follow upstream: jHydraSDR 1.1.3 wraps libhydrasdr 1.1.3. The
upstream version is pinned in [`native/versions.env`](native/versions.env), which
is the only place the version is set.

| Trigger | What happens |
|---------|--------------|
| Pull request | Builds and tests all six platforms; publishes nothing |
| Merge to `main` | Replaces the `nightly` pre-release, built against upstream `main` |
| Daily schedule | The same, if this project or upstream has changed |
| Tag `vX.Y.Z` | Publishes a release built from the pinned upstream version |

To release, tag the pinned version: `git tag v1.1.3 && git push origin v1.1.3`.
The workflow rejects a tag that does not match the pin. For a Java-only fix on
the same upstream version add a fourth number, such as `v1.1.3.1`; for a
pre-release add a suffix, such as `v1.1.3-rc1`.

To adopt a new upstream release, change `HYDRASDR_REF` in
`native/versions.env`, which also moves the jHydraSDR version. If upstream added functions, the build lists them; add
them to `Native.java` and any new structure fields to `Layouts.java`.

## License

Apache License 2.0. The bundled native libraries have their own licenses: see
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
