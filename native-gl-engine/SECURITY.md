# Security Policy

## Supported Versions

| Version | Supported |
|---|---|
| 0.1.x | ✅ Active |

## Reporting a Vulnerability

If you discover a security vulnerability in NativeGLEngine, please report it responsibly.

### How to report

1. **DO NOT** open a public GitHub Issue for security vulnerabilities
2. Send an email to the maintainer via the GitHub profile: [Eaielectronic](https://github.com/Eaielectronic)
3. Include:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

### Response timeline

- **Acknowledgment**: Within 48 hours
- **Initial assessment**: Within 1 week
- **Fix release**: Within 2 weeks for critical issues

## Security considerations specific to NativeGLEngine

### Native code (.so)

NativeGLEngine includes a precompiled native library (`libNativeGLEngine.so`) for ARM64. This library:

- Is compiled from the C++ source code in `src/main/cpp/`
- Uses only standard Android NDK APIs (`liblog`, `libandroid`, `libEGL`, `libGLESv3`, `libvulkan`)
- Does **not** access the network
- Does **not** access personal files
- Reads only:
  - `/proc/meminfo` (system memory info, read-only, public)
  - `/sys/class/thermal/thermal_zone*/temp` (thermal sensors, read-only, public)
- Uses `dlopen(RTLD_NOLOAD)` to find already-loaded GL libraries (does not load new libraries)

### PLT Hooking

The GL interceptor uses PLT (Procedure Linkage Table) hooking to redirect OpenGL calls. This:

- Only affects the current process (Minecraft JVM)
- Does not modify system libraries on disk
- Is reversible (hooks can be uninstalled at runtime)
- Is disabled by default and requires explicit opt-in via config

### JNI Bridge

All Java-to-native calls go through `jni_bridge.cpp` with proper input validation and error handling. JNI pointers are released correctly to prevent memory leaks.

### Permissions

NativeGLEngine does **not** request any special Android permissions. It operates within the existing Minecraft/launcher sandbox.
