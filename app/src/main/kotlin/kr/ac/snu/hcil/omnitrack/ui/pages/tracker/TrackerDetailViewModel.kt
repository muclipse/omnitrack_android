package kr.ac.snu.hcil.omnitrack.ui.pages.tracker

import android.app.Application
import android.support.annotation.DrawableRes
import dagger.Lazy
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.realm.Realm
import io.realm.RealmChangeListener
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttributeManager
import kr.ac.snu.hcil.omnitrack.core.auth.OTAuthManager
import kr.ac.snu.hcil.omnitrack.core.connection.OTConnection
import kr.ac.snu.hcil.omnitrack.core.database.local.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.core.database.local.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.database.local.OTTriggerDAO
import kr.ac.snu.hcil.omnitrack.core.database.synchronization.ESyncDataType
import kr.ac.snu.hcil.omnitrack.core.database.synchronization.OTSyncManager
import kr.ac.snu.hcil.omnitrack.core.database.synchronization.SyncDirection
import kr.ac.snu.hcil.omnitrack.ui.viewmodels.RealmViewModel
import kr.ac.snu.hcil.omnitrack.utils.*
import org.jetbrains.anko.collections.forEachWithIndex
import java.util.*
import javax.inject.Inject
import kotlin.collections.HashSet

/**
 * Created by younghokim on 2017. 10. 3..
 */
class TrackerDetailViewModel(app: Application) : RealmViewModel(app) {

    @Inject
    protected lateinit var attributeManager: Lazy<OTAttributeManager>

    @Inject
    protected lateinit var authManager: Lazy<OTAuthManager>

    @Inject
    protected lateinit var syncManager: OTSyncManager


    private var isInitialized = false

    val isInitializedObservable = BehaviorSubject.create<Boolean>()

    val isEditMode: Boolean get() = trackerDao != null

    var trackerDao: OTTrackerDAO? = null
        private set

    private var initialSnapshotDao: OTTrackerDAO? = null

    val trackerId: String? get() = this.trackerDao?.objectId

    //Observables========================================
    val trackerIdObservable = BehaviorSubject.createDefault<Nullable<String>>(Nullable(null))

    val reminderCountObservable = BehaviorSubject.createDefault<Int>(0)

    val nameObservable = BehaviorSubject.createDefault<String>("")
    val isBookmarkedObservable = BehaviorSubject.createDefault<Boolean>(false)
    val colorObservable = BehaviorSubject.createDefault<Int>(OTApp.instance.colorPalette[0])
    val attributeViewModelListObservable = BehaviorSubject.create<List<AttributeInformationViewModel>>()
    //===================================================

    var name: String
        get() = nameObservable.value
        set(value) {
            if (trackerDao != null) {
                realm.executeTransactionIfNotIn {
                    trackerDao?.name = value
                    trackerDao?.synchronizedAt = null
                }
                registerSyncJob()
            } else if (value != nameObservable.value) {
                nameObservable.onNext(value)
            }
        }

    var isBookmarked: Boolean
        get() = isBookmarkedObservable.value
        set(value) {
            if (trackerDao != null) {
                realm.executeTransactionIfNotIn {
                    trackerDao?.isBookmarked = value
                    trackerDao?.synchronizedAt = null
                }
                registerSyncJob()
            } else if (value != isBookmarkedObservable.value) {
                isBookmarkedObservable.onNext(value)
            }
        }

    var color: Int
        get() = colorObservable.value
        set(value) {
            if (trackerDao != null) {
                realm.executeTransactionIfNotIn {
                    trackerDao?.color = value
                    trackerDao?.synchronizedAt = null
                }
                registerSyncJob()
            } else if (value != colorObservable.value) {
                colorObservable.onNext(value)
            }
        }

    val onChangesApplied = PublishSubject.create<String>()

    private val removedAttributes = HashSet<AttributeInformationViewModel>()

    private val currentAttributeViewModelList = ArrayList<AttributeInformationViewModel>()

    fun registerSyncJob() {
        syncManager.registerSyncQueue(ESyncDataType.TRACKER, SyncDirection.UPLOAD)
    }

    override fun onInject(app: OTApp) {
        app.synchronizationComponent.inject(this)
    }

    fun init(trackerId: String?) {
        if (!isInitialized) {
            subscriptions.clear()
            if (trackerId != null) {
                val dao = dbManager.get().getTrackerQueryWithId(trackerId, realm).findFirstAsync()

                subscriptions.add(
                        dao.asFlowable<OTTrackerDAO>().filter { it.isValid && it.isLoaded }.subscribe { snapshot ->
                            if (initialSnapshotDao == null)
                                initialSnapshotDao = realm.copyFromRealm(snapshot)

                            nameObservable.onNextIfDifferAndNotNull(snapshot.name)
                            isBookmarkedObservable.onNextIfDifferAndNotNull(snapshot.isBookmarked)
                            colorObservable.onNextIfDifferAndNotNull(snapshot.color)

                            if (trackerDao != dao) {
                                subscriptions.add(
                                        snapshot.makeAttributesQuery(false, null).findAllAsync().asChangesetObservable().subscribe { changes ->
                                            val changeset = changes.changeset
                                            if (changeset == null) {
                                                //initial
                                                clearCurrentAttributeList()
                                                currentAttributeViewModelList.addAll(changes.collection.map { AttributeInformationViewModel(it, realm, attributeManager.get()) })
                                            } else {
                                                currentAttributeViewModelList.removeAll(
                                                        changeset.deletions.map { currentAttributeViewModelList[it].apply { this.unregister() } }
                                                )

                                                changeset.insertions.forEach {
                                                    currentAttributeViewModelList.add(it,
                                                            AttributeInformationViewModel(changes.collection[it]!!, realm, attributeManager.get())
                                                    )
                                                }
                                            }

                                            attributeViewModelListObservable.onNext(currentAttributeViewModelList)
                                        }
                                )


                                snapshot.liveTriggersQuery?.let {
                                    subscriptions.add(
                                            it.equalTo("actionType", OTTriggerDAO.ACTION_TYPE_REMIND)
                                                    .findAllAsync()
                                                    .asFlowable()
                                                    .subscribe {
                                                        reminderCountObservable.onNextIfDifferAndNotNull(it.size)
                                                    }
                                    )
                                }

                                trackerDao = dao
                            }
                        }
                )

                subscriptions.add(
                        dbManager.get().makeShortcutPanelRefreshObservable(authManager.get().userId!!, realm).subscribe { list ->
                            println("shortcut refresh")
                        }
                )

                /*
                attributeRealmResults?.removeChangeListener(attributeListChangedListener)
                attributeRealmResults = OTApp.instance.databaseManager.getAttributeListQuery(trackerId, realm).findAllSortedAsync("position")
                attributeRealmResults?.addChangeListener(attributeListChangedListener)*/

            } else trackerDao = null

            trackerIdObservable.onNext(Nullable(trackerId))

            isInitialized = true
            isInitializedObservable.onNext(true)
        }
    }

    fun addNewAttribute(name: String, type: Int, processor: ((OTAttributeDAO, Realm) -> OTAttributeDAO)? = null) {
        val newDao = OTAttributeDAO()
        newDao.objectId = UUID.randomUUID().toString()
        newDao.name = name
        newDao.type = type
        newDao.trackerId = trackerId
        newDao.initialize()
        processor?.invoke(newDao, realm)

        if (trackerDao != null) {
            newDao.localId = attributeManager.get().makeNewAttributeLocalId(newDao.userCreatedAt)
            newDao.trackerId = trackerDao?.objectId

            realm.executeTransactionIfNotIn {
                trackerDao?.attributes?.add(newDao)
            }
        } else {
            currentAttributeViewModelList.add(AttributeInformationViewModel(newDao, realm, attributeManager.get()))
            attributeViewModelListObservable.onNext(currentAttributeViewModelList)
        }
    }

    fun moveAttribute(from: Int, to: Int) {
        if (trackerDao != null) {
            val attributes = currentAttributeViewModelList.map { it.attributeDAO }.toMutableList()
            attributes.move(from, to)
            realm.executeTransactionIfNotIn {
                trackerDao?.attributes?.removeAll(attributes)
                trackerDao?.attributes?.addAll(0, attributes)
                trackerDao?.synchronizedAt = null
            }
            registerSyncJob()
        } else {
            currentAttributeViewModelList.move(from, to)
            attributeViewModelListObservable.onNext(currentAttributeViewModelList)
        }
    }

    fun removeAttribute(attrViewModel: AttributeInformationViewModel) {
        if (trackerDao != null) {
            realm.executeTransactionIfNotIn {
                val attribute = trackerDao?.attributes?.find { it.objectId == attrViewModel.objectId }
                if (attribute != null) {
                    attribute.isInTrashcan = true
                }
                trackerDao?.synchronizedAt = null
            }
            registerSyncJob()
        } else {
            removedAttributes.add(attrViewModel)
            currentAttributeViewModelList.remove(attrViewModel)
            attributeViewModelListObservable.onNext(currentAttributeViewModelList)
        }
    }

    val isNameDirty: Boolean get() = if (initialSnapshotDao == null) false else initialSnapshotDao?.name != nameObservable.value
    val isBookmarkedDirty: Boolean get() = if (initialSnapshotDao == null) false else initialSnapshotDao?.isBookmarked != isBookmarkedObservable.value
    val isColorDirty: Boolean get() = if (initialSnapshotDao == null) false else initialSnapshotDao?.color != colorObservable.value

    val areAttributesDirty: Boolean
        get() {
            return if (initialSnapshotDao == null) false else (currentAttributeViewModelList.find { it.isDirty == true } != null) ||
                    !Arrays.equals(initialSnapshotDao?.attributes?.map { attr -> attr.objectId }?.toTypedArray(), currentAttributeViewModelList.map { it.attributeDAO.objectId }.toTypedArray())
        }

    val isDirty: Boolean
        get() {
            return if (initialSnapshotDao == null) false
            else isNameDirty || isBookmarkedDirty || isColorDirty || areAttributesDirty
        }

    fun applyChanges(): String {
        if (trackerDao == null) {
            realm.executeTransaction {
                val trackerDao = realm.createObject(OTTrackerDAO::class.java, UUID.randomUUID().toString())
                trackerDao.userId = authManager.get().userId
                trackerDao.name = nameObservable.value
                trackerDao.isBookmarked = isBookmarkedObservable.value
                trackerDao.color = colorObservable.value

                currentAttributeViewModelList.forEachWithIndex { index, attrViewModel ->
                    if (!attrViewModel.isInDatabase) {
                        attrViewModel.attributeDAO.localId = attributeManager.get().makeNewAttributeLocalId(attrViewModel.attributeDAO.userCreatedAt)
                        attrViewModel.attributeDAO.trackerId = trackerDao.objectId
                        attrViewModel.saveToRealm()
                    }
                    attrViewModel.attributeDAO.position = index
                    attrViewModel.applyChanges()
                    trackerDao.attributes.add(attrViewModel.attributeDAO)
                    trackerDao.synchronizedAt = null
                }

                removedAttributes.clear()

                this.trackerDao = trackerDao
            }

            currentAttributeViewModelList.forEach {
                it.register()
            }

            registerSyncJob()
        }

        onChangesApplied.onNext(trackerDao!!.objectId!!)

        return trackerDao!!.objectId!!
    }

    private fun clearCurrentAttributeList() {
        currentAttributeViewModelList.forEach {
            it.unregister()
        }
        currentAttributeViewModelList.clear()
    }


    override fun onCleared() {
        super.onCleared()
        clearCurrentAttributeList()
        removedAttributes.clear()
    }

    class AttributeInformationViewModel(_attributeDAO: OTAttributeDAO, val realm: Realm, val attributeManager: OTAttributeManager) : IReadonlyObjectId, RealmChangeListener<OTAttributeDAO> {
        override val objectId: String?
            get() = attributeDAO.objectId

        var attributeDAO: OTAttributeDAO = _attributeDAO
            private set

        val isInDatabase: Boolean get() = attributeDAO.isManaged

        val nameObservable = BehaviorSubject.createDefault<String>("")
        val isRequiredObservable = BehaviorSubject.createDefault<Boolean>(false)
        val typeObservable = BehaviorSubject.createDefault<Int>(-1)
        val iconObservable = BehaviorSubject.createDefault<Int>(R.drawable.icon_small_longtext)
        val connectionObservable = BehaviorSubject.create<Nullable<OTConnection>>()
        val defaultValuePolicyObservable = BehaviorSubject.createDefault<Int>(OTAttributeDAO.DEFAULT_VALUE_POLICY_NULL)
        val defaultValuePresetObservable = BehaviorSubject.createDefault<Nullable<String>>(Nullable<String>(null))

        val isHiddenObservable = BehaviorSubject.createDefault(false)

        val onPropertyChanged = PublishSubject.create<Long>()

        val internalSubscription = CompositeDisposable()

        //key, dbId/serializedValue
        private val propertyTable = Hashtable<String, Pair<String, String>>()

        var name: String
            get() = nameObservable.value
            set(value) {
                if (nameObservable.value != value) {
                    nameObservable.onNext(value)
                }
            }

        var icon: Int
            get() = iconObservable.value
            set(@DrawableRes value) {
                if (iconObservable.value != value) {
                    iconObservable.onNext(value)
                }
            }

        var isRequired: Boolean
            get() = isRequiredObservable.value
            set(value) {
                if (isRequiredObservable.value != value) {
                    isRequiredObservable.onNext(value)
                }
            }

        var isHidden: Boolean
            get() = isHiddenObservable.value
            set(value) {
                if (isHiddenObservable.value != value) {
                    isHiddenObservable.onNext(value)
                }
            }

        var defaultValuePolicy: Int
            get() = defaultValuePolicyObservable.value
            set(value) {
                if (value != defaultValuePolicyObservable.value) {
                    defaultValuePolicyObservable.onNext(value)
                }
            }

        var defaultValuePreset: String?
            get() = defaultValuePresetObservable.value.datum
            set(value) {
                if (value != defaultValuePresetObservable.value.datum) {
                    defaultValuePresetObservable.onNext(Nullable(value))
                }
            }

        init {
            if (isInDatabase)
                attributeDAO.addChangeListener(this)

            onChange(attributeDAO)

        }

        fun unregister() {
            if (isInDatabase)
                attributeDAO.removeChangeListener(this)

            internalSubscription.clear()
        }

        override fun onChange(snapshot: OTAttributeDAO) {
            if (snapshot.isValid) {
                applyDaoChangesToFront(snapshot)
            }
        }

        private fun arePropertiesDirty(): Boolean {
            for (entry in propertyTable) {
                if (attributeDAO.getPropertySerializedValue(entry.key) != entry.value.second)
                    return true
            }
            return false
        }

        val isDirty: Boolean
            get() {
                return attributeDAO.localId.isBlank()
                        || attributeDAO.name != name
                        || attributeDAO.isRequired != isRequired
                        || attributeDAO.fallbackValuePolicy != defaultValuePolicy
                        || attributeDAO.fallbackPresetSerializedValue != defaultValuePreset
                        || attributeDAO.serializedConnection != connectionObservable.value?.datum?.getSerializedString()
                        || attributeDAO.isHidden != isHidden
                        || arePropertiesDirty()
            }

        fun makeFrontalChangesToDao(): OTAttributeDAO {
            val dao = OTAttributeDAO()
            dao.objectId = attributeDAO.objectId
            dao.type = attributeDAO.type
            dao.localId = attributeDAO.localId
            dao.position = attributeDAO.position
            dao.trackerId = attributeDAO.trackerId
            dao.userUpdatedAt = attributeDAO.userUpdatedAt

            dao.isRequired = this.isRequired
            dao.fallbackValuePolicy = this.defaultValuePolicy
            dao.fallbackPresetSerializedValue = this.defaultValuePreset
            dao.isHidden = this.isHidden
            dao.name = name
            for (entry in propertyTable) {
                dao.setPropertySerializedValue(entry.key, entry.value.second)
            }

            dao.serializedConnection = connectionObservable.value?.datum?.getSerializedString()

            return dao
        }

        fun applyDaoChangesToFront(editedDao: OTAttributeDAO) {
            name = editedDao.name

            isRequired = editedDao.isRequired

            defaultValuePolicy = editedDao.fallbackValuePolicy
            defaultValuePreset = editedDao.fallbackPresetSerializedValue
            isHidden = editedDao.isHidden

            println("serialized connection of dao: ${editedDao.serializedConnection}")
            editedDao.serializedConnection?.let {
                val connection = OTConnection.fromJson(it)
                if (connectionObservable.value?.datum != connection) {
                    connectionObservable.onNext(Nullable(connection))
                }
            } ?: if (connectionObservable.value?.datum != null) {
                connectionObservable.onNext(Nullable(null))
            }

            propertyTable.clear()
            var changed = false
            editedDao.properties.forEach { entry ->
                if (propertyTable[entry.key] != Pair(entry.id, entry.value!!)) {
                    propertyTable[entry.key] = Pair(entry.id, entry.value!!)
                    changed = true
                }
            }

            val helper = OTAttributeManager.getAttributeHelper(editedDao.type)
            icon = helper.getTypeSmallIconResourceId(editedDao)
            if (changed) {
                onPropertyChanged.onNext(System.currentTimeMillis())
            }

            if (typeObservable.value != editedDao.type) {
                typeObservable.onNext(editedDao.type)
            }
        }

        fun applyChanges() {
            realm.executeTransactionIfNotIn {
                attributeDAO.name = name
                attributeDAO.isHidden = isHidden
                attributeDAO.isRequired = isRequired
                attributeDAO.userUpdatedAt = System.currentTimeMillis()
                attributeDAO.fallbackValuePolicy = defaultValuePolicy
                attributeDAO.fallbackPresetSerializedValue = defaultValuePreset
                for (entry in propertyTable) {
                    println("set new serialized value: $entry")
                    attributeDAO.setPropertySerializedValue(entry.key, entry.value.second)
                    println("get serialized value: ${attributeDAO.getPropertySerializedValue(entry.key)}")
                }
                attributeDAO.serializedConnection = connectionObservable.value?.datum?.getSerializedString()
            }
        }

        fun saveToRealm() {
            this.attributeDAO = realm.copyToRealm(this.attributeDAO)
        }

        fun register() {
            if (isInDatabase)
                attributeDAO.addChangeListener(this)
        }
    }
}