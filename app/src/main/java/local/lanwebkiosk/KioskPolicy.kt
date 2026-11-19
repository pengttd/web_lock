package local.lanwebkiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log

internal class KioskPolicy(private val context: Context) {
    fun releaseLegacyLock() {
        val policy = context.getSystemService(DevicePolicyManager::class.java) ?: return
        val packageName = context.packageName
        if (!policy.isDeviceOwnerApp(packageName)) return

        val admin = ComponentName(context, KioskAdminReceiver::class.java)
        try {
            val allowedPackages = policy.getLockTaskPackages(admin)
            if (packageName in allowedPackages) {
                val remainingPackages = allowedPackages.filterNot { it == packageName }.toTypedArray()
                policy.setLockTaskPackages(admin, remainingPackages)
            }
        } catch (error: SecurityException) {
            Log.w(TAG, "System rejected legacy lock task removal", error)
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Could not remove legacy lock task policy", error)
        }
    }

    private companion object {
        const val TAG = "KioskPolicy"
    }
}
