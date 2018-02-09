package kr.ac.snu.hcil.omnitrack.services

import android.content.SharedPreferences
import com.firebase.jobdispatcher.FirebaseJobDispatcher
import com.firebase.jobdispatcher.Job
import com.firebase.jobdispatcher.JobParameters
import com.firebase.jobdispatcher.JobService
import com.github.javiersantos.appupdater.AppUpdaterUtils
import com.github.javiersantos.appupdater.enums.AppUpdaterError
import com.github.javiersantos.appupdater.objects.Update
import dagger.Lazy
import kr.ac.snu.hcil.omnitrack.core.di.global.Default
import kr.ac.snu.hcil.omnitrack.core.di.global.VersionCheck
import kr.ac.snu.hcil.omnitrack.core.system.OTAppVersionCheckManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by younghokim on 2017. 4. 15..
 */
class OTVersionCheckService : JobService() {


    companion object {
        const val TAG = "VersionCheckService"
        const val REQUEST_CODE = 20
        const val PREF_CHECK_UPDATES = "pref_check_updates"
        const val PREF_LAST_NOTIFIED_VERSION = "last_notified_version"
    }

    @Singleton
    class Controller @Inject constructor(@Default val pref: Lazy<SharedPreferences>, val dispatcher: Lazy<FirebaseJobDispatcher>, @VersionCheck val job: Lazy<Job>) {

        val versionCheckSwitchTurnedOn: Boolean
            get() = pref.get().getBoolean(PREF_CHECK_UPDATES, false)

        fun turnOnService(){
            dispatcher.get().schedule(job.get())
        }

        fun turnOffService(){
            dispatcher.get().cancel(TAG)
        }
    }

    private lateinit var updaterUtils: AppUpdaterUtils

    override fun onCreate() {
        super.onCreate()
        updaterUtils = OTAppVersionCheckManager.makeAppUpdaterUtils(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        updaterUtils.stop()
    }

    override fun onStartJob(job: JobParameters): Boolean {
        updaterUtils.withListener(object : AppUpdaterUtils.UpdateListener {
            override fun onSuccess(update: Update, isUpdateAvailable: Boolean) {

                this@OTVersionCheckService.jobFinished(job, false)
            }

            override fun onFailed(error: AppUpdaterError) {
                this@OTVersionCheckService.jobFinished(job, true)
            }

        })
        updaterUtils.start()
        return true
    }

    override fun onStopJob(job: JobParameters): Boolean {
        updaterUtils.stop()
        return true
    }

/*
    private val checkSubscription = SerialDisposable()

    @field:[Inject Default]
    lateinit var systemPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        (application as OTApp).jobDispatcherComponent.inject(this)
    }

    override fun onStartJob(job: JobParameters): Boolean {
        synchronized(checkSubscription)
        {
            Log.d(TAG, "check latest version of OmniTrack...: Service")
            RemoteConfigManager.getServerLatestVersionName().doAfterTerminate {
                jobFinished(job, true)
            }.subscribe({
                versionName ->
                Log.d(TAG, "received version name.: ${versionName}")
                if (BuildConfig.DEBUG || RemoteConfigManager.isNewVersionGreater(BuildConfig.VERSION_NAME, versionName)) {
                    val lastNotifiedVersion = systemPreferences.getString(PREF_LAST_NOTIFIED_VERSION, "")
                    if (lastNotifiedVersion != versionName) {

                        if (!OTApp.instance.isAppInForeground) {
                            Log.d(TAG, "instance is in background. send notification.")
                            val notification = NotificationCompat.Builder(this, OTNotificationManager.CHANNEL_ID_NOTICE)
                                    .setColor(ContextCompat.getColor(this, R.color.colorPointed))
                                    .setContentTitle(OTApp.getString(R.string.msg_notification_new_version_detected_title))
                                    .setContentText(OTApp.getString(R.string.msg_notification_new_version_detected_text))
                                    .setLargeIcon(VectorIconHelper.getConvertedBitmap(this, R.drawable.icon_simple))
                                    .setSmallIcon(R.drawable.icon_simple)
                                    .setDefaults(Notification.DEFAULT_ALL)
                                    .setAutoCancel(true)
                                    .setContentIntent(
                                            PendingIntent.getActivity(this, REQUEST_CODE,
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=kr.ac.snu.hcil.omnitrack")),
                                                    PendingIntent.FLAG_UPDATE_CURRENT)
                                    )
                                    .build()
                            notificationManager.notify(TAG, REQUEST_CODE, notification)

                            systemPreferences.edit()
                                    .putString(PREF_LAST_NOTIFIED_VERSION, versionName)
                                    .apply()
                        } else {

                            Log.d(TAG, "instance is in foreground. send broadcast.")
                            this.notificationManager.cancel(TAG, REQUEST_CODE)
                            val intent = Intent(OTApp.BROADCAST_ACTION_NEW_VERSION_DETECTED)
                            intent.putExtra(OTApp.INTENT_EXTRA_LATEST_VERSION_NAME, versionName)

                            sendBroadcast(intent)
                        }
                    } else {
                        Log.d(TAG, "this version was already notified. ignore notification.")
                    }
                }
            }, { e -> e.printStackTrace()  })
        }
        return true
    }

    override fun onStopJob(job: JobParameters): Boolean {
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        checkSubscription.set(Disposables.empty())
    }*/
}