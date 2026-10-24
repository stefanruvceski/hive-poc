package app.stride.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Tells the open app that something outside it (notification action, background work) changed the stored data. */
object DataEvents {
    private val _changed = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val changed: SharedFlow<Unit> = _changed.asSharedFlow()

    fun notifyChanged() {
        _changed.tryEmit(Unit)
    }
}
