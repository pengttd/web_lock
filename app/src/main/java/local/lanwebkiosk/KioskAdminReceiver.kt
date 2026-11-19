package local.lanwebkiosk

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

class KioskAdminReceiver : DeviceAdminReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            KioskPolicy(context).releaseLegacyLock()
            return
        }
        super.onReceive(context, intent)
    }
}
