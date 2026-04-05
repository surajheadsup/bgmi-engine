package com.bgmi.engine

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader

object ShizukuManager {

    private const val TAG = "ShizukuManager"
    private const val REQUEST_CODE = 1001

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private var cachedService: IShizukuService? = null

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                Log.d(TAG, "Permission result: granted=$granted")
                permissionCallback?.invoke(granted)
                permissionCallback = null
            }
        }

    fun init() {
        try {
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add permission listener", e)
        }
    }

    fun destroy() {
        try {
            Shizuku.removeRequestPermissionResultListener(permissionResultListener)
            cachedService = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove permission listener", e)
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.e(TAG, "Shizuku ping failed", e)
            false
        }
    }

    fun hasPermission(): Boolean {
        return try {
            if (!isShizukuAvailable()) return false
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.e(TAG, "Permission check failed", e)
            false
        }
    }

    fun requestPermission(callback: (Boolean) -> Unit) {
        try {
            if (!isShizukuAvailable()) {
                callback(false)
                return
            }
            if (hasPermission()) {
                callback(true)
                return
            }
            permissionCallback = callback
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            Log.e(TAG, "Request permission failed", e)
            callback(false)
        }
    }

    fun runCommand(cmd: String): CommandResult {
        if (!hasPermission()) {
            return CommandResult(false, "", "Shizuku permission not granted")
        }

        // Try Method 1: IShizukuService.newProcess via reflection binder
        val result1 = runViaShizukuService(cmd)
        if (result1 != null) return result1

        // Try Method 2: Reflection on Shizuku.newProcess
        val result2 = runViaReflection(cmd)
        if (result2 != null) return result2

        return CommandResult(false, "", "All Shizuku execution methods failed")
    }

    /**
     * Method 1: Get Shizuku binder via reflection → IShizukuService → newProcess
     */
    private fun runViaShizukuService(cmd: String): CommandResult? {
        return try {
            val service = getShizukuService() ?: return null
            val args = arrayOf("sh", "-c", cmd)

            val remoteProcess = service.newProcess(args, null, null)
                ?: return CommandResult(false, "", "newProcess returned null")

            readProcessResult(remoteProcess, cmd)
        } catch (e: Exception) {
            Log.e(TAG, "Method1 (IShizukuService) failed: ${e.message}")
            cachedService = null // Reset cache on failure
            null
        }
    }

    /**
     * Method 2: Call Shizuku.newProcess via reflection (may be private)
     */
    private fun runViaReflection(cmd: String): CommandResult? {
        return try {
            val args = arrayOf("sh", "-c", cmd)

            // Try different method signatures
            val methods = Shizuku::class.java.declaredMethods
            for (method in methods) {
                if (method.name == "newProcess") {
                    method.isAccessible = true
                    val process = method.invoke(null, args, null as Array<String>?, null as String?) as? Process
                    if (process != null) {
                        return readJavaProcessResult(process, cmd)
                    }
                }
            }

            Log.e(TAG, "Method2 (reflection): newProcess method not found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Method2 (reflection) failed: ${e.message}")
            null
        }
    }

    /**
     * Get IShizukuService by reflecting into Shizuku to get its binder
     */
    private fun getShizukuService(): IShizukuService? {
        if (cachedService != null) {
            try {
                // Verify it's still alive
                cachedService?.version
                return cachedService
            } catch (e: Exception) {
                cachedService = null
            }
        }

        return try {
            // Reflect to get the binder from Shizuku
            val binderField = Shizuku::class.java.getDeclaredField("binder")
            binderField.isAccessible = true
            val binder = binderField.get(null) as? IBinder

            if (binder != null) {
                val service = IShizukuService.Stub.asInterface(binder)
                cachedService = service
                Log.d(TAG, "Got IShizukuService via binder field")
                return service
            }

            // Try alternative: sService field
            try {
                val serviceField = Shizuku::class.java.getDeclaredField("sService")
                serviceField.isAccessible = true
                val svc = serviceField.get(null)
                if (svc is IShizukuService) {
                    cachedService = svc
                    Log.d(TAG, "Got IShizukuService via sService field")
                    return svc
                }
            } catch (_: NoSuchFieldException) {}

            // Try getBinder method
            try {
                val getBinderMethod = Shizuku::class.java.getDeclaredMethod("getBinder")
                getBinderMethod.isAccessible = true
                val b = getBinderMethod.invoke(null) as? IBinder
                if (b != null) {
                    val service = IShizukuService.Stub.asInterface(b)
                    cachedService = service
                    Log.d(TAG, "Got IShizukuService via getBinder()")
                    return service
                }
            } catch (_: NoSuchMethodException) {}

            Log.e(TAG, "Could not obtain IShizukuService through any method")
            null
        } catch (e: Exception) {
            Log.e(TAG, "getShizukuService failed: ${e.message}")
            null
        }
    }

    /**
     * Read result from IRemoteProcess (Shizuku AIDL)
     */
    private fun readProcessResult(remoteProcess: IRemoteProcess, cmd: String): CommandResult {
        val stdoutPfd = remoteProcess.inputStream
        val stderrPfd = remoteProcess.errorStream

        val stdout = if (stdoutPfd != null) {
            BufferedReader(InputStreamReader(FileInputStream(stdoutPfd.fileDescriptor))).use { it.readText() }
        } else ""

        val stderr = if (stderrPfd != null) {
            BufferedReader(InputStreamReader(FileInputStream(stderrPfd.fileDescriptor))).use { it.readText() }
        } else ""

        val exitCode = remoteProcess.waitFor()

        stdoutPfd?.close()
        stderrPfd?.close()

        try { remoteProcess.destroy() } catch (_: Exception) {}

        return if (exitCode == 0) {
            Log.d(TAG, "Command success: $cmd")
            CommandResult(true, stdout.trim(), "")
        } else {
            Log.w(TAG, "Command failed ($exitCode): $cmd -> $stderr")
            CommandResult(false, stdout.trim(), "exit=$exitCode: ${stderr.trim()}")
        }
    }

    /**
     * Read result from java.lang.Process (reflection fallback)
     */
    private fun readJavaProcessResult(process: Process, cmd: String): CommandResult {
        val stdout = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val stderr = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
        val exitCode = process.waitFor()

        return if (exitCode == 0) {
            Log.d(TAG, "Command success (reflection): $cmd")
            CommandResult(true, stdout.trim(), "")
        } else {
            Log.w(TAG, "Command failed (reflection, $exitCode): $cmd -> $stderr")
            CommandResult(false, stdout.trim(), "exit=$exitCode: ${stderr.trim()}")
        }
    }

    fun runCommandSilent(cmd: String): Boolean {
        return runCommand(cmd).success
    }

    data class CommandResult(
        val success: Boolean,
        val output: String,
        val error: String
    )
}
