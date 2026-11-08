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

jHydraSDR does not contain libusb. It is supplied at runtime by
[jlibusb](https://github.com/DSheirer/jlibusb), which ships it under the GNU
Lesser General Public License version 2.1 or later and documents how to
replace it. jHydraSDR is built against the libusb headers of the release that
`native/versions.env` records.

## pthreads4w (Windows only)

Source: <https://github.com/jwinarske/pthreads4w>

Licensed under the Apache License 2.0 and statically linked into `hydrasdr.dll`.
