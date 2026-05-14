package com.turnit.ide.engine

import android.content.Context
import android.system.Os
import android.util.Log
import java.io.File

private const val TAG = "ShellEngine"

class ShellEngine(private val context: Context) {

    private var process: Process? = null
    private var outputCallback: ((String) -> Unit)? = null
    private var isRunning = false

    fun setOutputCallback(callback: (String) -> Unit) {
        outputCallback = callback
    }

    val isSessionActive: Boolean get() = isRunning

    fun startShell() {
        if (isRunning) {
            appendOutput("[ShellEngine] Session already active.")
            return
        }

        val shellPath = "/system/bin/sh"
        val args = listOf(shellPath)

        appendOutput("[ShellEngine] ── Native Shell ──────────────────────────────────")
        appendOutput("[ShellEngine] ${args.joinToString(" ")}")
        appendOutput("[ShellEngine] ────────────────────────────────────────────────")

        try {
            val binPath = setupToolchain()
            process = ProcessBuilder(args).apply {
                directory(context.filesDir)
                redirectErrorStream(false)

                environment().apply {
                    put("HOME", context.filesDir.absolutePath)
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("TERM", "xterm-256color")
                    put("LD_LIBRARY_PATH", context.applicationInfo.nativeLibraryDir)
                    put("PATH", "$binPath:/system/bin:/system/xbin:${context.applicationInfo.nativeLibraryDir}")
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

    private fun setupToolchain(): String {
        val binDir = File(context.filesDir, "bin").apply { mkdirs() }
        val libToybox = File(context.applicationInfo.nativeLibraryDir, "libtoybox.so")
        val toyboxLink = File(binDir, "toybox")
        try {
            Os.symlink(libToybox.absolutePath, toyboxLink.absolutePath)
        } catch (_: Exception) {
        }
        return binDir.absolutePath
    }

    private fun appendOutput(line: String) {
        Log.d(TAG, line)
        outputCallback?.invoke(line)
    }
}
