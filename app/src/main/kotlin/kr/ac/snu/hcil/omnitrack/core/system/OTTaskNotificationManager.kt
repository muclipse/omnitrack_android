package kr.ac.snu.hcil.omnitrack.core.system

import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.utils.VectorIconHelper
import org.jetbrains.anko.notificationManager

/**
 * Created by Young-Ho Kim on 2017-03-10.
 */
object OTTaskNotificationManager {

    const val PROGRESS_INDETERMINATE = -1

    fun setTaskProgressNotification(context: Context, tag: String? = null, id: Int, title: String, content: String, progress: Int, largeIcon: Int? = R.drawable.icon_cloud_download, smallIcon: Int = android.R.drawable.stat_sys_download, dismissedIntent: PendingIntent? = null) {
        val notification = makeTaskProgressNotificationBuilder(context, title, content, progress, largeIcon, smallIcon, dismissedIntent).build()

        if (tag != null) {
            context.notificationManager.notify(tag, id, notification)
        } else {
            context.notificationManager.notify(id, notification)
        }
    }

    fun makeTaskProgressNotificationBuilder(context: Context, title: String, content: String, progress: Int, largeIcon: Int? = R.drawable.icon_cloud_download, smallIcon: Int = android.R.drawable.stat_sys_download, dismissedIntent: PendingIntent? = null): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, OTNotificationManager.CHANNEL_ID_SYSTEM)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(smallIcon)
                .setOngoing(true)
                .setColor(ContextCompat.getColor(context, R.color.colorPrimary))
                .apply {
                    if (largeIcon != null) {
                        setLargeIcon(VectorIconHelper.getConvertedBitmap(context, largeIcon))
                    }
                }
                .setDeleteIntent(dismissedIntent)
                .setOnlyAlertOnce(true)
                .setProgress(100, progress, progress == PROGRESS_INDETERMINATE)
    }

    fun dismissNotification(context: Context, id: Int, tag: String? = null) {
        println("cancel notification")
        if (tag != null) {
            context.notificationManager.cancel(tag, id)
        } else context.notificationManager.cancel(id)
    }

}