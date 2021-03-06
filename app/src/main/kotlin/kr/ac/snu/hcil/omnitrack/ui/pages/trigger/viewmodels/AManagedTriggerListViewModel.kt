package kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels

import android.app.Application
import io.realm.OrderedCollectionChangeSet
import io.realm.RealmQuery
import io.realm.RealmResults
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.core.database.models.OTTriggerDAO
import kr.ac.snu.hcil.omnitrack.core.synchronization.ESyncDataType
import kr.ac.snu.hcil.omnitrack.core.synchronization.OTSyncManager
import kr.ac.snu.hcil.omnitrack.core.synchronization.SyncDirection
import javax.inject.Inject

/**
 * Created by younghokim on 2017. 10. 20..
 *
 * This class is a ViewModel of Android Architecture Component to be used for displaying and editing the triggers of a specific user.
 *
 * @author Young-Ho Kim
 *
 *
 */
abstract class AManagedTriggerListViewModel(app: Application) : ATriggerListViewModel(app) {

    @Inject
    protected lateinit var syncManager: OTSyncManager

    private var currentTriggerRealmResults: RealmResults<OTTriggerDAO>? = null

    /**
     * @param originalQuery Receives the original query in vanilla viewmodel. modify this
     * @return the modified query for Triggers. The vanilla implementation just returns the originalQuery.
     */
    protected open fun hookTriggerQuery(originalQuery: RealmQuery<OTTriggerDAO>): RealmQuery<OTTriggerDAO> {
        return originalQuery
    }

    override fun onInject(app: OTAndroidApp) {
        getApplication<OTApp>().applicationComponent.inject(this)
    }


    protected fun init() {
        onDispose()
        currentTriggerRealmResults = hookTriggerQuery(
                dbManager.get().makeTriggersOfUserVisibleQuery(authManager.userId!!, realm))
                .findAllAsync()

        currentTriggerRealmResults?.addChangeListener { snapshot, changeSet ->
            if (changeSet.state == OrderedCollectionChangeSet.State.INITIAL) {
                //initial set
                currentTriggerViewModels.clear()
                currentTriggerViewModels.addAll(
                        snapshot.map { TriggerViewModel(getApplication(), it, realm) }
                )
            } else {
                //changes
                //deal with deletions
                val removes = changeSet.deletions.map { i -> currentTriggerViewModels[i] }
                removes.forEach { it.unregister() }
                currentTriggerViewModels.removeAll(removes)

                //deal with additions
                changeSet.insertions.forEach { i ->
                    currentTriggerViewModels.add(i, TriggerViewModel(getApplication(), snapshot[i]!!, realm))
                }

                //deal with refresh
                changeSet.changes.forEach { i ->
                }
            }

            notifyNewTriggerViewModels()
        }
    }

    override fun addNewTriggerImpl(dao: OTTriggerDAO) {

        beforeAddNewTrigger(dao)

        dbManager.get().saveTrigger(dao, realm)
        syncManager.registerSyncQueue(ESyncDataType.TRIGGER, SyncDirection.UPLOAD, ignoreDirtyFlags = false)

    }

    override fun removeTrigger(objectId: String) {
        val viewModel = currentTriggerViewModels.find { it._id == objectId }
        if (viewModel != null) {
            dbManager.get().removeTrigger(viewModel.dao, false, realm)
            syncManager.registerSyncQueue(ESyncDataType.TRIGGER, SyncDirection.UPLOAD, ignoreDirtyFlags = false)
        }

    }

    protected fun onDispose() {
        currentTriggerViewModels.clear()
        currentTriggerRealmResults?.removeAllChangeListeners()
    }

    override fun onCleared() {
        onDispose()
        super.onCleared()
    }

}