package kr.ac.snu.hcil.omnitrack.ui.pages.tracker

import android.app.Application
import kr.ac.snu.hcil.omnitrack.core.database.models.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.database.models.OTTriggerDAO
import kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels.OfflineTriggerListViewModel
import kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels.TriggerInterfaceOptions

/**
 * Created by younghokim on 2017. 10. 24..
 */
class OfflineReminderListViewModel(app: Application) : OfflineTriggerListViewModel(app) {
    override val defaultTriggerInterfaceOptions: TriggerInterfaceOptions = TriggerInterfaceOptions(
            false,
            null,
            arrayOf(OTTriggerDAO.CONDITION_TYPE_TIME, OTTriggerDAO.CONDITION_TYPE_DATA),
            OTTriggerDAO.ACTION_TYPE_REMIND)


    fun applyToDb(trackerId: String) {

        realm.executeTransactionAsync { realm ->
            currentTriggerViewModels.map { it.dao }.forEach {
                beforeAddNewTrigger(it)
                if (it.trackers.find { it._id == trackerId } == null) {
                    val trackerDao = realm.where(OTTrackerDAO::class.java).equalTo("_id", trackerId).findFirst()
                    if (trackerDao != null)
                        it.trackers.add(realm.copyFromRealm(trackerDao))
                }
                dbManager.get().saveTrigger(it, realm)
            }
        }
    }
}