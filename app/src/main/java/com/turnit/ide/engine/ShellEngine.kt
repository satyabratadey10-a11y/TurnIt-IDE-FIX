package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import java.io.File
import java.io.InputStream

private const val TAG = "ShellEngine"

class ShellEngine(private val context: Context) {

    private var process: Process? = null
    private var outputCallback: ((String) -> Unit)? = null
    private var isRunning = false

    fun setOutputCallback(callback: (String) -> Unit) {
        outputCallback = callback
    }

    val isSessionActive: Boolean get() = isRunning

    data class BinarySet(
        val proot: File,
        val loader64: File,
        val loader32: File
    )

    private fun validateBinaries(): BinarySet? {
        val dir = context.applicationInfo.nativeLibraryDir

        appendOutput("[ShellEngine] ── Pre-flight binary validation ──────────────────")
        appendOutput("[ShellEngine] nativeLibraryDir = $dir")

        val installed = File(dir).listFiles()
        if (installed == null) {
            appendOutput("[ShellEngine] FATAL: nativeLibraryDir is null or unreadable")
            return null
        }
        appendOutput("[ShellEngine] Installed native files:")
        installed.forEach { f ->
            appendOutput("[ShellEngine]   ${f.name}  exists=${f.exists()}  canExec=${f.canExecute()}  size=${f.length()}")
        }

        val proot = File(dir, "libproot.so")
        val loader64 = File(dir, "libproot_loader64.so")
        val loader32 = File(dir, "libproot_loader32.so")

        var ok = true
        listOf(
            "libproot.so" to proot,
            "libproot_loader64.so" to loader64
        ).forEach { (name, file) ->
            val verdict = when {
                !file.exists() -> "MISSING — check Gradle packaging config"
                !file.canExecute() -> "EXISTS BUT NOT EXECUTABLE — SELinux blocked exec"
                file.length() == 0L -> "ZERO BYTES — packaging corruption"
                else -> "OK (${file.length()} bytes)"
            }
            appendOutput("[ShellEngine] $name: $verdict")
            if (verdict != "OK (${file.length()} bytes)") ok = false
        }

        if (loader32.exists()) {
            appendOutput("[ShellEngine] libproot_loader32.so: OK (${loader32.length()} bytes)")
        } else {
            appendOutput("[ShellEngine] libproot_loader32.so: OPTIONAL/MISSING")
        }

        if (!ok) {
            appendOutput("[ShellEngine] ── Pre-flight FAILED — aborting launch ───────")
            appendOutput("[ShellEngine] If binaries are MISSING: extractNativeLibs=true and useLegacyPackaging=true must be set")
            return null
        }

        appendOutput("[ShellEngine] ── Pre-flight PASSED ─────────────────────────────")
        return BinarySet(proot, loader64, loader32)
    }

    private fun setupTallocSymlink(): String {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        val tallocSo = File(nativeDir, "libtalloc.so")

        if (!tallocSo.exists()) {
            appendOutput("[ShellEngine] libtalloc.so not found — assuming static linkage, skipping symlink")
            return nativeDir
        }

        val libLinkDir = File(context.filesDir, "lib_links").also { it.mkdirs() }
        val tallocLink = File(libLinkDir, "libtalloc.so.2")

        if (!tallocLink.exists()) {
            try {
                java.nio.file.Files.createSymbolicLink(tallocLink.toPath(), tallocSo.toPath())
                appendOutput("[ShellEngine] Created talloc symlink: ${tallocLink.absolutePath} → ${tallocSo.absolutePath}")
            } catch (e: Exception) {
                appendOutput("[ShellEngine] Symlink creation failed: ${e.message} — falling back to nativeDir only")
                return nativeDir
            }
        }
        return "${libLinkDir.absolutePath}:$nativeDir"
    }

    fun startProot(rootfsPath: String, command: String = "/usr/bin/bash") {
        if (isRunning) {
            appendOutput("[ShellEngine] Session already active.")
            return
        }

        val binaries = validateBinaries() ?: return
        val ldPath = setupTallocSymlink()
        val safeCommand = if (command == "/bin/sh") "/usr/bin/bash" else command
        val args = buildProotArgs(binaries, rootfsPath, safeCommand)

        appendOutput("[ShellEngine] ── Environment ──────────────────────────────────")
        appendOutput("[ShellEngine] PROOT_LOADER    = ${binaries.loader64.absolutePath}")
        appendOutput("[ShellEngine] LD_LIBRARY_PATH = $ldPath")
        appendOutput("[ShellEngine] PROOT_NO_SECCOMP = 1 (pure ptrace mode — correct)")
        appendOutput("[ShellEngine] ── Command ─────────────────────────────────────")
        appendOutput("[ShellEngine] ${args.joinToString(" ")}")
        appendOutput("[ShellEngine] ────────────────────────────────────────────────")

        try {
            process = ProcessBuilder(args).apply {
                directory(context.filesDir)
                redirectErrorStream(false)

                environment().apply {
                    put("PROOT_LOADER", binaries.loader64.absolutePath)
                    if (binaries.loader32.exists()) put("PROOT_LOADER_32", binaries.loader32.absolutePath)
                    put("PROOT_NO_SECCOMP", "1")
                    put("LD_LIBRARY_PATH", ldPath)
                    put("PROOT_TMP_DIR", context.cacheDir.absolutePath)
                    put("HOME", "/root")
                    put("TERM", "xterm-256color")
                    put("LANG", "en_US.UTF-8")
                    put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                    put("SHELL", "/bin/bash")
                    put("USER", "root")
                    put("LOGNAME", "root")
                    put("LD_PRELOAD", "")
                }
            }.start().also { proc ->
                isRunning = true
                pipeStream(proc.inputStream, "")
                pipeStream(proc.errorStream, "[ERR] ")
                watchExit(proc)
            }

        } catch (e: Exception) {
            appendOutput("[ShellEngine] FATAL — ProcessBuilder threw: ${e.message}")
            Log.e(TAG, "ProcessBuilder failure", e)
            isRunning = false
        }
    }

    private fun buildProotArgs(bins: BinarySet, rootfsPath: String, command: String): List<String> = buildList {
        add(bins.proot.absolutePath)
        add("--kill-on-exit")
        add("--link2symlink")
        add("--sysvipc")
        add("--cwd=/root")
        add("-r"); add(rootfsPath)

        listOf("/proc", "/sys", "/dev", "/dev/pts").forEach { path ->
            add("-b"); add("$path:$path")
        }

        val devShm = File("/dev/shm")
        if (!devShm.exists()) devShm.mkdirs()
        add("-b"); add("/dev/shm:/dev/shm")

        add("-b"); add("${context.filesDir.absolutePath}:/android/data")
        add("--")
        addAll(command.split(" "))
    }

    fun sendInput(text: String) {
        if (!isRunning) { appendOutput("[ShellEngine] No active session."); return }
        try {
            process!!.outputStream.write((text + "\n").toByteArray())
            process!!.outputStream.flush()
        } catch (e: Exception) {
            appendOutput("[ShellEngine] Input error: ${e.message}")
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        isRunning = false
        appendOutput("[ShellEngine] Session stopped.")
    }

    private fun pipeStream(stream: java.io.InputStream, prefix: String) {
        Thread {
            try { stream.bufferedReader().forEachLine { appendOutput("$prefix$it") } }
            catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }

    private fun watchExit(proc: Process) {
        Thread {
            val code = proc.waitFor()
            isRunning = false
            appendOutput("[ShellEngine] Exited — code $code")
        }.apply { isDaemon = true; start() }
    }

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
