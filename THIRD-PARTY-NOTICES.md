# Third-party notices

jHydraSDR is licensed under the Apache License 2.0. The jar also contains native
libraries from the projects below, which keep their own licenses. Full license
texts are in `META-INF/licenses/` inside the jar.

## libhydrasdr

Source: <https://github.com/hydrasdr/hydrasdr-host>

Licensed under the MIT and BSD-3-Clause licenses. See
`META-INF/licenses/libhydrasdr-LICENSE.md`. jHydraSDR is an independent project
and is not endorsed by HydraSDR or its contributors.

## libusb

Source: <https://github.com/libusb/libusb>

Licensed under the GNU Lesser General Public License version 2.1 or later. See
`META-INF/licenses/libusb-COPYING`. The exact version in each jar is recorded in
`jhydrasdr/native/<platform>/build-info.properties`, and
`native/versions.env` in this repository records the release it was built from.

libusb is shipped as a separate shared library so it can be replaced: point the
`jhydrasdr.library.path` system property at a directory containing your own
build along with libhydrasdr.

## pthreads4w (Windows only)

Source: <https://github.com/jwinarske/pthreads4w>

Licensed under the Apache License 2.0 and statically linked into `hydrasdr.dll`.
