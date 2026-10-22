package app.stride.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stride.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    /** [progress] is 0..1, or null while the download size is unknown. */
    data class Downloading(val progress: Float?) : UpdatePhase
    data object Failed : UpdatePhase
}

data class UpdateUiState(
    /** Newer release to offer, or null to show nothing. */
    val release: ReleaseInfo? = null,
    val phase: UpdatePhase = UpdatePhase.Idle,
    /** Show the explanation before sending the user to "Install unknown apps". */
    val askPermission: Boolean = false,
)

/**
 * Checks GitHub Releases on launch and whenever the app returns to the foreground,
 * and drives the update banner. Disabled in debug builds.
 */
class UpdateViewModel : ViewModel() {
    private val enabled = !BuildConfig.DEBUG
    private val installedBuild = BuildConfig.VERSION_CODE

    private var latest: ReleaseInfo? = null
    // Kept in memory only: a dismissed build stays hidden until the next app launch.
    private var dismissedBuild = 0
    private var checkJob: Job? = null
    private var downloadJob: Job? = null
    private var installAfterPermission = false

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    fun onForeground(context: Context) {
        if (!enabled) return
        if (installAfterPermission && Updater.canInstall(context)) {
            installAfterPermission = false
            install(context)
        }
        if (checkJob?.isActive == true) return
        checkJob = viewModelScope.launch {
            // Offline, rate limited or a bad response: keep quiet and keep whatever we had.
            val releases = Updater.fetchReleases() ?: return@launch
            latest = UpdateLogic.newerRelease(releases, installedBuild)
            if (downloadJob?.isActive != true) publish()
        }
    }

    private fun publish() {
        val show = latest?.takeIf { UpdateLogic.shouldShow(it, dismissedBuild) }
        _state.update { s -> if (s.release == show) s else UpdateUiState(release = show) }
    }

    fun dismiss() {
        val release = _state.value.release ?: return
        dismissedBuild = maxOf(dismissedBuild, release.build)
        downloadJob?.cancel()
        installAfterPermission = false
        publish()
    }

    fun install(context: Context) {
        val release = _state.value.release ?: return
        if (downloadJob?.isActive == true) return
        val app = context.applicationContext
        if (!Updater.canInstall(app)) {
            _state.update { it.copy(askPermission = true) }
            return
        }
        downloadJob = viewModelScope.launch {
            _state.update { it.copy(phase = UpdatePhase.Downloading(0f)) }
            try {
                val apk = Updater.download(app, release) { p ->
                    _state.update { it.copy(phase = UpdatePhase.Downloading(p)) }
                }
                val opened = Updater.launchInstaller(app, apk)
                _state.update { it.copy(phase = if (opened) UpdatePhase.Idle else UpdatePhase.Failed) }
            } catch (e: CancellationException) {
                _state.update { it.copy(phase = UpdatePhase.Idle) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(phase = UpdatePhase.Failed) }
            }
        }
    }

    fun openPermissionSettings(context: Context) {
        _state.update { it.copy(askPermission = false) }
        installAfterPermission = Updater.openInstallPermissionSettings(context)
        if (!installAfterPermission) _state.update { it.copy(phase = UpdatePhase.Failed) }
    }

    fun cancelPermission() {
        _state.update { it.copy(askPermission = false) }
    }
}
