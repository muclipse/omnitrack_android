package kr.ac.snu.hcil.omnitrack.services.messaging

import com.firebase.jobdispatcher.FirebaseJobDispatcher
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dagger.internal.Factory
import io.reactivex.Completable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.core.configuration.ConfiguredContext
import kr.ac.snu.hcil.omnitrack.core.configuration.OTConfigurationController
import kr.ac.snu.hcil.omnitrack.core.database.global.OTAttachedConfigurationDao
import kr.ac.snu.hcil.omnitrack.core.di.global.AppLevelDatabase
import kr.ac.snu.hcil.omnitrack.core.synchronization.ESyncDataType
import kr.ac.snu.hcil.omnitrack.core.synchronization.SyncDirection
import javax.inject.Inject

/**
 * Created by younghokim on 2017. 4. 12..
 */
class OTFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val COMMAND_SYNC = "sync_down"
        const val COMMAND_SIGNOUT = "sign_out"
        const val COMMAND_DUMP_DB = "dump_db"
    }

    @Inject
    lateinit var dispatcher: FirebaseJobDispatcher

    @Inject
    lateinit var configController: OTConfigurationController

    @field:[Inject AppLevelDatabase]
    lateinit var realmFactory: Factory<Realm>

    private val subscriptions = CompositeDisposable()

    override fun onCreate() {
        super.onCreate()
        (application as OTApp).applicationComponent.inject(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        subscriptions.clear()
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        println("received Firebase Cloud message")
        val receiverToken = remoteMessage.to
        if (receiverToken != null) {
            subscriptions.add(
                    Completable.defer {
                        realmFactory.get().use { realm ->
                            val dao = realm.where(OTAttachedConfigurationDao::class.java)
                                    .equalTo(OTAttachedConfigurationDao.FIELD_INSTANCE_ID, receiverToken).findFirst()
                            if (dao != null) {
                                val configuredContext = configController.getConfiguredContextOf(dao.staticConfiguration())
                                if (configuredContext != null) {
                                    val data = remoteMessage.data
                                    if (data != null && data.size > 0) {
                                        try {
                                            when (data.get("command")) {
                                                COMMAND_SYNC -> handleSyncCommand(data, configuredContext)
                                                COMMAND_SIGNOUT -> handleSignOutCommand(data, configuredContext)
                                                COMMAND_DUMP_DB -> handleDumpCommand(data, configuredContext)
                                            }
                                        } catch (ex: Exception) {
                                            ex.printStackTrace()
                                        }
                                    }
                                }
                            }
                        }
                        return@defer Completable.complete()
                    }.subscribeOn(Schedulers.io()).subscribe {

                    }
            )
        }
    }

    private fun handleSyncCommand(data: Map<String, String>, configuredContext: ConfiguredContext) {
        println("received Firebase Cloud message - sync")
        val syncInfoArray = Gson().fromJson<JsonArray>(data.get("syncInfoArray"), JsonArray::class.java)
        var registeredCount = 0
        syncInfoArray.forEach { syncInfo ->
            val typeString = (syncInfo as JsonObject).get("type")?.asString
            if (typeString != null) {
                configuredContext.configuredAppComponent.getSyncManager().registerSyncQueue(ESyncDataType.valueOf(typeString), SyncDirection.DOWNLOAD, false)
                registeredCount++
            }
        }

        if (registeredCount > 0) {

            configuredContext.configuredAppComponent.getSyncManager().reserveSyncServiceNow()
        }
    }

    private fun handleDumpCommand(data: Map<String, String>, configuredContext: ConfiguredContext) {

    }

    private fun handleSignOutCommand(data: Map<String, String>, configuredContext: ConfiguredContext) {
        configuredContext.configuredAppComponent.getAuthManager().signOut()
    }
}