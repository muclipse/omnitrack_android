package kr.ac.snu.hcil.omnitrack.core.triggers

import android.content.Context
import dagger.Lazy
import dagger.internal.Factory
import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.core.database.configured.BackendDbManager
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.OTTriggerDAO
import kr.ac.snu.hcil.omnitrack.core.system.OTExternalSettingsPrompter

/**
 * Created by younghokim on 2017. 11. 9..
 */
class OTTriggerSystemManager(
        val triggerAlarmManager: Lazy<ITriggerAlarmController>,
        val realmProvider: Factory<Realm>,
        val context: Context
) {
    private val settingsPrompter: OTExternalSettingsPrompter by lazy {
        OTExternalSettingsPrompter(context)
    }

    fun onSystemRebooted() {
        triggerAlarmManager.get().activateOnSystem()
    }

    fun handleTriggerOn(managedTrigger: OTTriggerDAO) {
        println("TriggerSystemManager: handleTriggerOn: ${managedTrigger.objectId}")
        when (managedTrigger.conditionType) {
            OTTriggerDAO.CONDITION_TYPE_TIME -> {
                triggerAlarmManager.get().registerTriggerAlarm(System.currentTimeMillis(), managedTrigger)
            }
        }
    }

    fun handleTriggerOff(managedTrigger: OTTriggerDAO) {
        println("TriggerSystemManager: handleTriggerOff: ${managedTrigger.objectId}")
        when (managedTrigger.conditionType) {
            OTTriggerDAO.CONDITION_TYPE_TIME -> {
                triggerAlarmManager.get().cancelTrigger(managedTrigger)
            }
        }
    }


    fun tryCheckInToSystem(managedTrigger: OTTriggerDAO): Boolean {
        println("TriggerSystemManager: tryCheckInToSystem: ${managedTrigger.objectId}")
        if (managedTrigger.isOn) {
            when (managedTrigger.conditionType) {
                OTTriggerDAO.CONDITION_TYPE_TIME -> {
                    triggerAlarmManager.get().continueTriggerInChainIfPossible(managedTrigger)
                    if (managedTrigger.isOn) {
                        if (!settingsPrompter.isBatteryOptimizationWhiteListed()) {
                            settingsPrompter.askUserBatterOptimizationWhitelist()
                        }
                    }
                }
            }
        } else {
            handleTriggerOff(managedTrigger)
        }
        return false
    }

    fun tryCheckOutFromSystem(managedTrigger: OTTriggerDAO): Boolean {
        println("TriggerSystemManager: tryCheckOutFromSystem: ${managedTrigger.objectId}")
        handleTriggerOff(managedTrigger)
        return false
    }

    fun refreshReservedAlarms() {
        triggerAlarmManager.get().rearrangeSystemAlarms()
    }

    fun checkOutAllFromSystem(userId: String): Int {
        val realm = realmProvider.get()

        val triggers = realm.where(OTTriggerDAO::class.java)
                .equalTo(BackendDbManager.FIELD_USER_ID, userId)
                .equalTo("isOn", true)
                .findAll()

        triggers.forEach { trigger ->
            tryCheckOutFromSystem(trigger)
        }
        val numTriggers = triggers.size
        realm.close()
        return numTriggers
    }

    fun checkInAllToSystem(userId: String): Int {
        val realm = realmProvider.get()

        val triggers = realm.where(OTTriggerDAO::class.java)
                .equalTo(BackendDbManager.FIELD_USER_ID, userId)
                .equalTo("isOn", true)
                .findAll()
        triggers.forEach { trigger ->
            if (trigger.liveTrackerCount > 0) {
                tryCheckInToSystem(trigger)
            }
        }
        val numTriggers = triggers.size
        realm.close()
        return numTriggers
    }
}