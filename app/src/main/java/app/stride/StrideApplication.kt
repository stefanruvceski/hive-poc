package app.stride

import android.app.Application
import app.stride.host.AppInfo

/** Runs before any activity, widget or background work: shares build facts with the modules and starts them. */
class StrideApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppInfo.init(BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME, BuildConfig.DEBUG)
        app.stride.format.Dates.init(this)
        Modules.all.forEach { it.onAppStart(this) }
    }
}
