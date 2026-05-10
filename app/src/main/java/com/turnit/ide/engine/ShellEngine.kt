package com.turnit.ide.engine

import android.content.Context
import android.util.Log
import android.system.Os
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

    fun startProot(rootfsPath: String, command: String = "/usr/bin/bash") {
        if (isRunning) {
            appendOutput("[ShellEngine-V2] Session already active.")
            return
        }

        val nativeDir = context.applicationInfo.nativeLibraryDir
        val prootNative = File(nativeDir, "libproot.so")
        val loaderNative = File(nativeDir, "libproot_loader64.so")
        val tallocNative = File(nativeDir, "libtalloc.so")

        if (!prootNative.exists() || !loaderNative.exists()) {
            appendOutput("[FATAL] Required native binaries missing from APK.")
            return
        }

        // CRITICAL BYPASS: The Staging Area. 
        // We copy binaries out of the restricted nativeDir and use Kotlin to force executable permissions.
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val proot = File(binDir, "proot")
        val loader64 = File(binDir, "proot_loader64")
        val talloc = File(binDir, "libtalloc.so")
        val tallocLink = File(binDir, "libtalloc.so.2")

        try {
            if (!proot.exists() || proot.length() != prootNative.length()) {
                prootNative.copyTo(proot, overwrite = true)
                proot.setExecutable(true) // Uses modern, kernel-approved syscalls
            }
            if (!loader64.exists() || loader64.length() != loaderNative.length()) {
                loaderNative.copyTo(loader64, overwrite = true)
                loader64.setExecutable(true)
            }
            if (tallocNative.exists() && (!talloc.exists() || talloc.length() != tallocNative.length())) {
                tallocNative.copyTo(talloc, overwrite = true)
            }
            if (talloc.exists() && !tallocLink.exists()) {
                Os.symlink(talloc.absolutePath, tallocLink.absolutePath)
            }
        } catch (e: Exception) {
            appendOutput("[FATAL] Staging failed: ${e.message}")
            return
        }

        val safeCommand = if (command == "/bin/sh") "/usr/bin/bash" else command

        val prootArgs = buildList {
            add(proot.absolutePath) // Execute the staged binary, not the native one
            add("--kill-on-exit")
            add("--link2symlink")
            add("--sysvipc")
            add("-0")
            add("-r"); add(rootfsPath)
            add("-w"); add("/root")
            
            // Core System Mounts
            listOf("/dev", "/proc", "/sys").forEach {
                add("-b"); add(it)
            }
            
            // Host data bridge
            add("-b"); add("${context.filesDir.absolutePath}:/android/data")
            
            addAll(safeCommand.split(" "))
        }

        appendOutput("[ShellEngine-V2] ─────────────────────────────────────")
        appendOutput("[ShellEngine-V2] Launching Ultimate PRoot Architecture...")
        appendOutput("[ShellEngine-V2] Loader path : ${loader64.absolutePath}")
        appendOutput("[ShellEngine-V2] ─────────────────────────────────────")

        try {
            val pb = ProcessBuilder(prootArgs).apply {
                directory(context.filesDir)
                redirectErrorStream(false)
                
                environment().apply {
                    // PRoot will now see the X_OK execute bit is true, and skip the cache fallback!
                    put("PROOT_LOADER", loader64.absolutePath)
                    put("PROOT_NO_SECCOMP", "1")
                    put("PROOT_TMP_DIR", context.cacheDir.absolutePath)
                    put("HOME", "/root")
                    put("TERM", "xterm-256color")
                    put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                    
                    // Point dynamic linker to our staged directory
                    put("LD_LIBRARY_PATH", binDir.absolutePath) 
                }
            }

            process = pb.start().also { proc ->
                isRunning = true
                pipeStream(proc.inputStream, prefix = "")
                pipeStream(proc.errorStream, prefix = "[ERR] ")
                watchExit(proc)
            }

        } catch (e: Exception) {
            val msg = "[ShellEngine-V2] FATAL: ProcessBuilder threw — ${e.message}"
            Log.e(TAG, msg, e)
            appendOutput(msg)
            isRunning = false
        }
    }

    fun sendInput(text: String) {
        if (process == null || !isRunning) {
            appendOutput("[ShellEngine-V2] No active session.")
            return
        }
        try {
            process!!.outputStream.write((text + "\n").toByteArray())
            process!!.outputStream.flush()
        } catch (e: Exception) {
            appendOutput("[ShellEngine-V2] Input write failed: ${e.message}")
        }
    }

    fun stop() {
        process?.destroy()
        process = null
        isRunning = false
        appendOutput("[ShellEngine-V2] Session stopped.")
    }

    val isSessionActive: Boolean get() = isRunning

    private fun pipeStream(stream: InputStream, prefix: String) {
        Thread {
            try {
                stream.bufferedReader().forEachLine { line ->
                    appendOutput("$prefix$line")
                }
            } catch (_: Exception) {}
        }.apply { isDaemon = true; start() }
    }

    private fun watchExit(proc: Process) {
        Thread {
            val code = proc.waitFor()
            isRunning = false
            appendOutput("[ShellEngine-V2] Process exited — code $code")
        }.apply { isDaemon = true; start() }
    }

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
