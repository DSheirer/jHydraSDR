#!/usr/bin/env bash
# Builds libusb and libhydrasdr as shared libraries for the host platform and
# copies them to dist/native/<platform>. Works on Linux and macOS.
#
# Usage: native/build-unix.sh [hydrasdr-git-ref]
#
# libusb stays a separate shared library because it is LGPL-2.1: keeping it out
# of libhydrasdr lets users replace it. It is built without udev so the Linux
# binaries do not need libudev at runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=versions.env
source "$ROOT/native/versions.env"
REF="${1:-$HYDRASDR_REF}"
WORK="${JHYDRASDR_WORK_DIR:-$ROOT/build/native-work}"
JOBS="$(getconf _NPROCESSORS_ONLN)"

case "$(uname -s)" in
    Linux)  OS=linux ;;
    Darwin) OS=macos ;;
    *) echo "Unsupported OS: $(uname -s)" >&2; exit 1 ;;
esac
case "$(uname -m)" in
    x86_64|amd64)  ARCH=x86_64 ;;
    aarch64|arm64) ARCH=aarch64 ;;
    *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac
PLATFORM="$OS-$ARCH"
OUT="$ROOT/dist/native/$PLATFORM"

rm -rf "$WORK" "$OUT"
mkdir -p "$WORK" "$OUT" "$ROOT/dist/native/licenses"
cd "$WORK"

echo "=== libusb $LIBUSB_VERSION ==="
TARBALL="libusb-$LIBUSB_VERSION.tar.bz2"
curl -fsSL -o "$TARBALL" \
    "https://github.com/libusb/libusb/releases/download/v$LIBUSB_VERSION/$TARBALL"
if command -v sha256sum >/dev/null; then
    echo "$LIBUSB_TARBALL_SHA256  $TARBALL" | sha256sum -c -
else
    echo "$LIBUSB_TARBALL_SHA256  $TARBALL" | shasum -a 256 -c -
fi
tar xjf "$TARBALL"

MIN_MACOS=11.0
USB_PREFIX="$WORK/usb"
USB_ARGS=(--prefix="$USB_PREFIX" --disable-static --enable-shared)
if [ "$OS" = linux ]; then
    USB_ARGS+=(--disable-udev)
else
    export CFLAGS="-O2 -mmacosx-version-min=$MIN_MACOS"
    export LDFLAGS="-mmacosx-version-min=$MIN_MACOS"
fi
(cd "libusb-$LIBUSB_VERSION" && ./configure "${USB_ARGS[@]}" >/dev/null && make -j"$JOBS" install >/dev/null)
unset CFLAGS LDFLAGS

echo "=== libhydrasdr $REF ==="
git clone --quiet --depth 1 --branch "$REF" "$HYDRASDR_REPOSITORY" hydrasdr
UPSTREAM_COMMIT="$(git -C hydrasdr rev-parse HEAD)"

CMAKE_ARGS=(
    -S hydrasdr/libhydrasdr -B build-hydrasdr
    -DCMAKE_BUILD_TYPE=Release
    -DENABLE_STATIC_LIB=OFF
    -DENABLE_SHARED_LIB=ON
    -DLIBUSB_INCLUDE_DIR="$USB_PREFIX/include/libusb-1.0"
)
if [ "$OS" = linux ]; then
    USB_LIB="$USB_PREFIX/lib/libusb-1.0.so"
    # $ORIGIN only: the build tree's paths must not leak into the shipped library.
    CMAKE_ARGS+=(-DCMAKE_BUILD_WITH_INSTALL_RPATH=ON -DCMAKE_INSTALL_RPATH='$ORIGIN')
else
    USB_LIB="$USB_PREFIX/lib/libusb-1.0.0.dylib"
    CMAKE_ARGS+=(-DCMAKE_OSX_DEPLOYMENT_TARGET="$MIN_MACOS")
fi
cmake "${CMAKE_ARGS[@]}" -DLIBUSB_LIBRARIES="$USB_LIB" >/dev/null
cmake --build build-hydrasdr --parallel "$JOBS" >/dev/null

echo "=== packaging $PLATFORM ==="
if [ "$OS" = linux ]; then
    cp -L "$USB_PREFIX/lib/libusb-1.0.so.0" "$OUT/libusb-1.0.so.0"
    cp -L build-hydrasdr/src/libhydrasdr.so "$OUT/libhydrasdr.so"
    chmod 755 "$OUT"/*
    # Only glibc and libusb may remain as dependencies.
    readelf -d "$OUT/libhydrasdr.so" | grep -E 'NEEDED|RUNPATH|RPATH'
else
    cp -L "$USB_PREFIX/lib/libusb-1.0.0.dylib" "$OUT/libusb-1.0.0.dylib"
    cp -L build-hydrasdr/src/libhydrasdr.dylib "$OUT/libhydrasdr.dylib"
    chmod 755 "$OUT"/*
    # Point libhydrasdr at the bundled libusb, not the absolute build path, and
    # re-sign because install_name_tool invalidates the ad-hoc signatures.
    install_name_tool -id @rpath/libusb-1.0.0.dylib "$OUT/libusb-1.0.0.dylib"
    install_name_tool -id @rpath/libhydrasdr.dylib "$OUT/libhydrasdr.dylib"
    install_name_tool -change "$USB_LIB" @loader_path/libusb-1.0.0.dylib "$OUT/libhydrasdr.dylib"
    codesign -f -s - "$OUT"/*.dylib
    otool -L "$OUT/libhydrasdr.dylib"
fi

# Pinned sources must match the bindings. Tracking upstream main only warns, so
# that one new upstream function does not stop the nightly builds.
if ! "$ROOT/native/check-bindings.sh" hydrasdr/libhydrasdr/src/hydrasdr.h; then
    [ "$REF" != "$HYDRASDR_REF" ] || exit 1
    echo "::warning::Bindings differ from hydrasdr.h at $REF"
fi

cp hydrasdr/libhydrasdr/LICENSE.md "$ROOT/dist/native/licenses/libhydrasdr-LICENSE.md"
cp "libusb-$LIBUSB_VERSION/COPYING" "$ROOT/dist/native/licenses/libusb-COPYING"
cat > "$OUT/build-info.properties" <<INFO
hydrasdr.ref=$REF
hydrasdr.commit=$UPSTREAM_COMMIT
libusb.version=$LIBUSB_VERSION
INFO
ls -l "$OUT"
