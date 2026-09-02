package com.pessoal.agenda.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pessoal.agenda.wear.contract.WearActionType
import com.pessoal.agenda.wear.data.WearAlertStore
import com.pessoal.agenda.wear.data.WearDatabase
import com.pessoal.agenda.wear.data.WearDeviceIdentity
import com.pessoal.agenda.wear.data.WearVisibleAlert
import com.pessoal.agenda.wear.sync.WearInitialStateReader
import com.pessoal.agenda.wear.sync.WearOutboxScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WearAgendaViewModel(application: Application) : AndroidViewModel(application) {
    private val identity = WearDeviceIdentity(application)
    private val store = WearAlertStore(WearDatabase.get(application), { identity.deviceId })
    private val outbox = WearOutboxScheduler(application)

    val alert: StateFlow<WearVisibleAlert?> = store.observeVisibleAlert().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        outbox.enqueue()
        viewModelScope.launch {
            runCatching { WearInitialStateReader(application).refresh(store) }
        }
    }

    fun complete(alertId: String) = record(alertId, WearActionType.COMPLETE, null)

    fun snooze(alertId: String, minutes: Int) = record(alertId, WearActionType.SNOOZE, minutes)

    fun dismissFeedback(alertId: String) {
        viewModelScope.launch { store.dismissFeedback(alertId) }
    }

    private fun record(alertId: String, action: WearActionType, minutes: Int?) {
        viewModelScope.launch {
            runCatching { store.recordAction(alertId, action, minutes) }
                .onSuccess { outbox.enqueue() }
        }
    }
}
