package vn.futaland.app.features.messaging

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.media.AudioManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vn.futaland.app.MainActivity
import vn.futaland.app.R

class FutaMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "futaland_alerts_v1"
        const val LEGACY_CHANNEL_ID = "futaland-important"
        const val EXTRA_ROUTE = "fcm_route"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val manager = context.getSystemService(NotificationManager::class.java) ?: return
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                for (chId in listOf(CHANNEL_ID, LEGACY_CHANNEL_ID)) {
                    val channel = NotificationChannel(
                        chId,
                        "Tin nhắn & thông báo quan trọng",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Thông báo tin nhắn trò chuyện và sự kiện quan trọng"
                        enableLights(true)
                        enableVibration(true)
                        vibrationPattern = longArrayOf(0, 250, 250, 250)
                        setSound(soundUri, audioAttributes)
                        lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { FcmRegistrar.register(this@FutaMessagingService, token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        val title = message.notification?.title ?: data["title"] ?: "FutaLand"
        val body = message.notification?.body ?: data["body"] ?: "Bạn có tin nhắn mới"
        val route = data["route"]
        data["unreadCount"]?.toIntOrNull()?.let { ChatUnreadBadge.set(it) }
        showNotification(title, body, route)
    }

    private fun showNotification(title: String, body: String, route: String?) {
        createChannel(this)
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!route.isNullOrEmpty()) {
                putExtra(EXTRA_ROUTE, route)
            }
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(soundUri, AudioManager.STREAM_NOTIFICATION)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()
        // Play notification ringtone directly so the user reliably hears an alert even when system suppresses heads-up in foreground
        try {
            val ringtone = RingtoneManager.getRingtone(applicationContext, soundUri)
            ringtone?.play()
        } catch (_: Exception) {}
        try {
            val managerCompat = NotificationManagerCompat.from(this)
            managerCompat.notify(System.currentTimeMillis().toInt(), notification)
            if (!managerCompat.areNotificationsEnabled()) {
                val ringtone = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ringtone?.play()
            }
        } catch (_: Exception) {
            try {
                val ringtone = RingtoneManager.getRingtone(applicationContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ringtone?.play()
            } catch (_: Exception) {}
        }
    }
}
