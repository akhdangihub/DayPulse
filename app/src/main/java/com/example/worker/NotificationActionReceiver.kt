package com.example.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.example.data.JournalRepository
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_APPROVE_CHUNK_TASKS) return
        val ids = intent.getLongArrayExtra(EXTRA_ACTION_IDS) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = JournalRepository(appContext)
                val actionDao = AppDatabase.getInstance(appContext).smartActionDao()
                for (id in ids) {
                    val item = actionDao.getById(id)
                    if (item != null) {
                        repo.acceptAction(item)
                    }
                }
                if (notifId != -1) {
                    NotificationManagerCompat.from(appContext).cancel(notifId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_APPROVE_CHUNK_TASKS = "com.example.action.APPROVE_CHUNK_TASKS"
        const val EXTRA_ACTION_IDS = "extra_action_ids"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
    }
}
