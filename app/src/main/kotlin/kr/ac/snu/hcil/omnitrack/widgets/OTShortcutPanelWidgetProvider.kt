package kr.ac.snu.hcil.omnitrack.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.core.ItemLoggingSource
import kr.ac.snu.hcil.omnitrack.core.database.BackendDbManager
import kr.ac.snu.hcil.omnitrack.core.database.models.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.triggers.OTReminderCommands
import kr.ac.snu.hcil.omnitrack.services.OTItemLoggingService
import kr.ac.snu.hcil.omnitrack.ui.pages.items.NewItemActivity

/**
 * Created by younghokim on 2017. 3. 30..
 */
class OTShortcutPanelWidgetProvider : AppWidgetProvider() {

    companion object {

        const val WIDGET_NAME = "Shortcut Panel Widget"

        const val ACTION_TRACKER_CLICK_EVENT = "${OTApp.PREFIX_ACTION}.APP_WIDGET_SHORTCUT_TRACKER_CLICKED"
        const val EXTRA_CLICK_COMMAND = "clickCommand"
        const val CLICK_COMMAND_ROW = "rowClicked"
        const val CLICK_COMMAND_INSTANT_LOGGING = "instantLoggingClicked"

        fun getAppWidgetIds(context: Context, appWidgetManager: AppWidgetManager?): IntArray {
            val _appWidgetManager = appWidgetManager ?: AppWidgetManager.getInstance(context)
            return _appWidgetManager.getAppWidgetIds(ComponentName(context, OTShortcutPanelWidgetProvider::class.java))
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val editor = OTShortcutPanelWidgetUpdateService.getPreferences(context).edit()
        for (id in appWidgetIds) {
            OTShortcutPanelWidgetUpdateService.removeVariables(id, editor)
        }
        editor.apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val app = context.applicationContext as OTAndroidApp
        app.applicationComponent.inject(this)
        if (intent.action == OTApp.BROADCAST_ACTION_USER_SIGNED_IN || intent.action == OTApp.BROADCAST_ACTION_USER_SIGNED_OUT) {
            val updateIntent = Intent(context, OTShortcutPanelWidgetUpdateService::class.java)

            updateIntent.action = when (intent.action) {
                OTApp.BROADCAST_ACTION_USER_SIGNED_IN -> OTShortcutPanelWidgetUpdateService.ACTION_TO_MAIN_MODE
                OTApp.BROADCAST_ACTION_USER_SIGNED_OUT -> OTShortcutPanelWidgetUpdateService.ACTION_TO_SIGN_IN_MODE
                else -> OTShortcutPanelWidgetUpdateService.ACTION_TO_SIGN_IN_MODE
            }
            val appWidgetManager = AppWidgetManager.getInstance(context)

            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, getAppWidgetIds(context, appWidgetManager))

            context.startService(updateIntent)
        } else if (intent.action == ACTION_TRACKER_CLICK_EVENT) {

            val trackerId = intent.getStringExtra(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER)
            val realm = app.applicationComponent.backendRealmFactory().get()
            val trackerDao = realm.where(OTTrackerDAO::class.java).equalTo(BackendDbManager.FIELD_OBJECT_ID, trackerId).findFirst()
            if (trackerDao?.isManualInputAllowed() == false) {
                val reminderCommands = OTReminderCommands(context)
                if (!reminderCommands.isReminderPromptingToTracker(trackerId)) {
                    Toast.makeText(context, "You cannot add new entry unless you are prompted by reminders.", Toast.LENGTH_LONG).show()
                    realm.close()
                    return
                }
            }

            when (intent.getStringExtra(EXTRA_CLICK_COMMAND)) {
                CLICK_COMMAND_ROW -> {
                    if (trackerId != null) {
                        if (trackerDao?.makeAttributesQuery(false, false)?.count() == 0L) {
                            context.startService(OTItemLoggingService.makeLoggingIntent(context, ItemLoggingSource.Shortcut, true, null, trackerId))
                        } else {
                            context.startActivity(NewItemActivity.makeNewItemPageIntent(trackerId, context).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                }

                CLICK_COMMAND_INSTANT_LOGGING -> {
                    if (trackerId != null) {
                        context.startService(OTItemLoggingService.makeLoggingIntent(context, ItemLoggingSource.Shortcut, true, null, trackerId))
                    }
                }
            }

            realm.close()
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {

        val intent = Intent(context, OTShortcutPanelWidgetUpdateService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
        intent.action = OTShortcutPanelWidgetUpdateService.ACTION_INITIALIZE

        context.startService(intent)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        //TODO
        //DatabaseManager.setUsedAppWidget(WIDGET_NAME, true)

    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val intent = Intent(context, OTShortcutPanelWidgetUpdateService::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        intent.action = OTShortcutPanelWidgetUpdateService.ACTION_RESIZED

        context.startService(intent)
    }
}