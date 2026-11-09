package online.aireport.split.data

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Tells the screens that group data on the server has changed (an expense was saved, a member
 * renamed, …) so they reload what they show.
 */
class GroupChanges {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    fun notifyChanged() {
        _events.tryEmit(Unit)
    }
}
