package app.stride.host

/** Build facts of the installed app, set once by the app's Application class (library modules have no BuildConfig of their own). */
object AppInfo {
    var versionCode: Int = 0
        private set
    var versionName: String = ""
        private set
    var debug: Boolean = true
        private set

    fun init(versionCode: Int, versionName: String, debug: Boolean) {
        this.versionCode = versionCode
        this.versionName = versionName
        this.debug = debug
    }
}
