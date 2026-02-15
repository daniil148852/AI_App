package com.ai.assistant

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltAndroidApp
class AiAssistantApp : Application() {

    companion object {
        var lastCrashLog: String? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                val stackTrace = sw.toString()

                val timestamp = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(Date())

                val crashReport = buildString {
                    appendLine("=== CRASH REPORT ===")
                    appendLine("Time: $timestamp")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable.javaClass.simpleName}")
                    appendLine("Message: ${throwable.message}")
                    appendLine("")
                    appendLine("Stack trace:")
                    appendLine(stackTrace)
                }

                lastCrashLog = crashReport

                // Save to file
                val crashFile = File(
                    getExternalFilesDir(null),
                    "crash_log.txt"
                )
                crashFile.appendText(crashReport + "\n\n")

                // Also save to internal storage as backup
                val internalFile = File(filesDir, "crash_log.txt")
                internalFile.appendText(crashReport + "\n\n")

                Log.e("CRASH", crashReport)

            } catch (e: Exception) {
                Log.e("CRASH", "Failed to save crash", e)
            }

            // Call default handler (shows system crash dialog)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
