package dev.creatorpulse

import android.app.Application
import android.content.Context
import dev.creatorpulse.data.AnalyticsCache
import dev.creatorpulse.data.Repository
import dev.creatorpulse.data.RobloxApi
import dev.creatorpulse.data.SecureStore
import dev.creatorpulse.data.Settings
import dev.creatorpulse.data.ThemeStore
import dev.creatorpulse.data.Updater
import dev.creatorpulse.widget.RefreshScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var secure: SecureStore; private set
    lateinit var settings: Settings; private set
    lateinit var api: RobloxApi; private set
    lateinit var repo: Repository; private set
    lateinit var themeStore: ThemeStore; private set
    lateinit var updater: Updater; private set
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        themeStore = ThemeStore(this)
        secure = SecureStore(this)
        settings = Settings(this)
        api = RobloxApi(secure)
        repo = Repository(this, secure, settings, api, AnalyticsCache(this))
        updater = Updater(this, api.client, api.json)
        scope.launch { RefreshScheduler.schedule(this@App, settings.refreshMinutes.first(), replace = false) }
    }

    companion object {
        fun get(context: Context): App = context.applicationContext as App
    }
}
