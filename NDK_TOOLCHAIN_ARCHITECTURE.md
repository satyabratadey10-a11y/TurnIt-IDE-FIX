# NDK Toolchain Architecture

## Objective
Transition the project to a fully native Android C toolchain by producing aarch64 Linux binaries for the core utilities we need inside the IDE runtime. The initial baseline is `lib7zr.so` built in CI. The next phase builds `toybox` (coreutils), `make`, and `clang` (LLVM) using the same NDK pipeline and packages them into the app for in-device compilation.

## Baseline Pipeline (Current State)
- GitHub Actions runs on Ubuntu.
- Android NDK (LLVM toolchain) is available via `ANDROID_NDK_HOME`.
- A proof-of-concept binary (`lib7zr.so`) is cross-compiled for `aarch64-linux-android26` and injected into `app/src/main/jniLibs/arm64-v8a`.

## Roadmap

### 1) Toolchain Preparation (CI Step)
- Export `TOOLCHAIN=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64`.
- Export `TARGET=aarch64-linux-android26` (API 26+ matches modern devices).
- Use `CC=$TOOLCHAIN/bin/$TARGET-clang` and `CXX=$TOOLCHAIN/bin/$TARGET-clang++` for all native builds.
- Set `AR`, `RANLIB`, `STRIP`, and `SYSROOT=$TOOLCHAIN/sysroot` explicitly to keep builds deterministic.

### 2) Compile toybox (coreutils replacement)
- Source: `https://github.com/landley/toybox`.
- Configure for static or shared (prefer static for portability):
  - `make defconfig`.
  - `make toybox` with `CC`, `CROSS_COMPILE`, and `SYSROOT` pointing to the NDK toolchain.
- Output target: `toybox` binary under `out/`.
- Packaging:
  - Place in `app/src/main/jniLibs/arm64-v8a/` as `libtoybox.so` (if shared) or in `app/src/main/assets/toolchain/` (if static binary).

### 3) Compile GNU make
- Source: `https://ftp.gnu.org/gnu/make/make-4.4.1.tar.gz`.
- Configure for Android:
  - `./configure --host=aarch64-linux-android --disable-nls --prefix=/data/data/com.turnit.ide/files/toolchain`.
  - Provide `CC`, `CFLAGS=--sysroot=$SYSROOT`, and `LDFLAGS=--sysroot=$SYSROOT`.
- Build with `make -j` and install into a staged directory.
- Packaging:
  - Copy `bin/make` into `app/src/main/assets/toolchain/bin/`.

### 4) Compile clang (LLVM)
- Source: `https://github.com/llvm/llvm-project`.
- Build with CMake using the NDK toolchain file:
  - ```
    cmake -G Ninja -S llvm -B build \
      -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a \
      -DANDROID_PLATFORM=26 \
      -DLLVM_ENABLE_PROJECTS=clang \
      -DLLVM_TARGETS_TO_BUILD=AArch64 \
      -DLLVM_ENABLE_TERMINFO=OFF \
      -DLLVM_ENABLE_LIBEDIT=OFF
    ```
- Produce `clang` and `clang++` binaries.
- Packaging:
  - Copy `bin/clang`, `bin/clang++`, and required runtime libs into `app/src/main/assets/toolchain/`.

### 5) Artifact Staging and App Integration
- Standardize output paths:
  - `app/src/main/assets/toolchain/bin/` for executables.
  - `app/src/main/jniLibs/arm64-v8a/` for shared libraries.
- Update runtime PATH in `ShellEngine` to include the toolchain bin directory when assets are unpacked to `filesDir` at first launch.

### 6) CI Packaging Flow
- Build each component in sequence (toybox → make → clang).
- Stage output under `toolchain-out/`.
- Copy to app packaging directories.
- Build APK with strict cache-busting flags to avoid stale artifacts.

## Deliverables
- Aarch64-native binaries for `toybox`, `make`, and `clang` built entirely with the Android NDK.
- APK packaged with the toolchain binaries and libraries, ready for on-device compilation without PRoot.
