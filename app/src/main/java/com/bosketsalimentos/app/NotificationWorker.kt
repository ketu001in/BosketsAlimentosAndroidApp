package com.bosketsalimentos.app

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.webkit.CookieManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Polls the website's unread-notification count using the signed-in session
 * cookie and raises a local notification when something is waiting.
 */
class NotificationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        if (!ServerConfig.isConfigured(ctx)) return@withContext Result.success()
        val site = ServerConfig.url(ctx)
        val cookie = try {
            CookieManager.getInstance().getCookie(site)
        } catch (_: Exception) {
            null
        } ?: return@withContext Result.success()

        try {
            val conn = URL("$site/api/notify.php?count=1").openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.setRequestProperty("Cookie", cookie)
            conn.setRequestProperty("User-Agent", "BosketsApp/" + BuildConfig.VERSION_NAME)
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val unread = JSONObject(body).optInt("unread", 0)
                if (unread > 0) showNotification(ctx, unread)
            }
            conn.disconnect()
        } catch (_: Exception) {
            // Network hiccups are fine — we'll try again next period.
        }
        Result.success()
    }

    private fun showNotification(ctx: Context, unread: Int) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
        ) {
            return
        }
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_PATH, "notifications.php")
        }
        val pending = PendingIntent.getActivity(
            ctx, 1001, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (unread == 1) "You have 1 new notification"
        else "You have $unread new notifications"
        val notification = NotificationCompat.Builder(ctx, App.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_splash_seal)
            .setContentTitle(ctx.getString(R.string.notif_title))
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        NotificationManagerCompat.from(ctx).notify(1001, notification)
    }
}
