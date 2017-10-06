package kr.ac.snu.hcil.omnitrack.ui.pages.tracker

import android.arch.lifecycle.ViewModel
import android.support.v7.util.DiffUtil
import io.realm.Realm
import io.realm.RealmChangeListener
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.core.auth.OTAuthManager
import kr.ac.snu.hcil.omnitrack.core.connection.OTConnection
import kr.ac.snu.hcil.omnitrack.core.database.local.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.core.database.local.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.utils.move
import org.jetbrains.anko.collections.forEachWithIndex
import rx.subjects.BehaviorSubject
import rx.subscriptions.CompositeSubscription
import java.util.*

/**
 * Created by younghokim on 2017. 10. 3..
 */
class TrackerDetailViewModel : ViewModel() {

    private var trackerDao: OTTrackerDAO? = null

    val trackerId: String? get() = this.trackerDao?.objectId

    private val realm: Realm = OTApplication.app.databaseManager.getRealmInstance()

    //Observables========================================
    val nameObservable = BehaviorSubject.create<String>("")
    val isBookmarkedObservable = BehaviorSubject.create<Boolean>(false)
    val colorObservable = BehaviorSubject.create<Int>(OTApplication.app.colorPalette[0])
    val attributeViewModelListObservable = BehaviorSubject.create<List<AttributeInformationViewModel>>()
    //===================================================

    var name: String
        get() = nameObservable.value
        set(value) {
            if (value != nameObservable.value) {
                nameObservable.onNext(value)
            }
        }

    var isBookmarked: Boolean
        get() = isBookmarkedObservable.value
        set(value) {
            if (value != isBookmarkedObservable.value) {
                isBookmarkedObservable.onNext(value)
            }
        }

    var color: Int
        get() = colorObservable.value
        set(value) {
            if (value != colorObservable.value) {
                colorObservable.onNext(value)
            }
        }

    private val subscriptions = CompositeSubscription()

    fun getAttributeViewModelList(): List<AttributeInformationViewModel> {
        return currentAttributeViewModelList
    }

    /*
        private var attributeRealmResults: RealmResults<OTAttributeDAO>? = null

        private val attributeListChangedListener = object: OrderedRealmCollectionChangeListener<RealmResults<OTAttributeDAO>>{
            override fun onChange(snapshot: RealmResults<OTAttributeDAO>, changeSet: OrderedCollectionChangeSet?) {
                println("Attribute list: ${snapshot.size}")

                if(changeSet == null)
                {
                    //first time emission
                    clearCurrentAttributeList()
                    currentAttributeViewModelList.addAll(
                            snapshot.map { AttributeInformationViewModel(it, realm) }
                    )
                }
                else{
                    //deal with deletions
                    val removes = changeSet.deletions.map { i -> currentAttributeViewModelList[i] }
                    removes.forEach { it.unregister() }
                    currentAttributeViewModelList.removeAll(removes)

                    //deal with additions
                    val newDaos = changeSet.insertions.map { i -> snapshot[i] }
                    currentAttributeViewModelList.addAll(
                            newDaos.map { AttributeInformationViewModel(it, realm) }
                    )
                }

                attributeViewModelListObservable.onNext(currentAttributeViewModelList)
            }
        }
    */
    private val currentAttributeViewModelList = ArrayList<AttributeInformationViewModel>()

    fun init(trackerId: String?) {
        if (trackerDao?.objectId != trackerId) {
            subscriptions.clear()
            if (trackerId != null) {
                val dao = OTApplication.app.databaseManager.getTrackerQueryWithId(trackerId, realm).findFirstAsync()

                subscriptions.add(
                        dao.asObservable<OTTrackerDAO>().filter { it.isValid }.first().subscribe { snapshot ->

                            name = snapshot.name
                            isBookmarked = snapshot.isBookmarked
                            color = snapshot.color

                            clearCurrentAttributeList()
                            currentAttributeViewModelList.addAll(snapshot.attributes.map { AttributeInformationViewModel(it, realm) })
                            attributeViewModelListObservable.onNext(currentAttributeViewModelList)
                        }
                )

                trackerDao = dao

                /*
                attributeRealmResults?.removeChangeListener(attributeListChangedListener)
                attributeRealmResults = OTApplication.app.databaseManager.getAttributeListQuery(trackerId, realm).findAllSortedAsync("position")
                attributeRealmResults?.addChangeListener(attributeListChangedListener)*/

            } else trackerDao = null
        }
    }

    fun addNewAttribute(name: String, type: Int) {
        val newDao = OTAttributeDAO()
        newDao.objectId = UUID.randomUUID().toString()
        newDao.name = name
        newDao.type = type
        newDao.trackerId = trackerId

        currentAttributeViewModelList.add(AttributeInformationViewModel(newDao, realm))
        attributeViewModelListObservable.onNext(currentAttributeViewModelList)
    }

    fun moveAttribute(from: Int, to: Int) {
        currentAttributeViewModelList.move(from, to)
    }

    private fun saveAttributes(trackerDao: OTTrackerDAO) {
        trackerDao.attributes.clear()
        currentAttributeViewModelList.forEachWithIndex { index, attrViewModel ->
            if (!attrViewModel.isInDatabase) {
                println("viewmodel ${attrViewModel.name} is not in database.")
                println("viewmodel attribute local id: ${trackerDao.attributeLocalKeySeed}")
                attrViewModel.attributeDAO.localId = trackerDao.attributeLocalKeySeed
                trackerDao.attributeLocalKeySeed++
                attrViewModel.attributeDAO.trackerId = trackerDao.objectId

                println("viewmodel attribute tracker id: ${trackerDao.objectId}")
                attrViewModel.saveToRealm()
            }
            attrViewModel.attributeDAO.position = index
            attrViewModel.applyChanges()
            trackerDao.attributes.add(attrViewModel.attributeDAO)
        }
    }

    fun applyChanges(): String {
        if (trackerDao != null) {
            trackerDao?.let { dao ->
                realm.executeTransaction {
                    dao.name = nameObservable.value
                    dao.isBookmarked = isBookmarkedObservable.value
                    dao.color = colorObservable.value
                    saveAttributes(dao)
                }
            }

        } else {
            realm.executeTransaction {
                val trackerDao = realm.createObject(OTTrackerDAO::class.java, UUID.randomUUID().toString())
                trackerDao.userId = OTAuthManager.userId
                trackerDao.name = nameObservable.value
                trackerDao.isBookmarked = isBookmarkedObservable.value
                trackerDao.color = colorObservable.value
                saveAttributes(trackerDao)

                this.trackerDao = trackerDao
            }
        }

        currentAttributeViewModelList.forEach {
            it.register()
        }

        return trackerDao!!.objectId!!
    }

    val hasChanges: Boolean
        get() {
            return trackerDao?.let { dao ->
                dao.name != nameObservable.value ||
                        dao.color != colorObservable.value ||
                        dao.isBookmarked != isBookmarkedObservable.value
            } ?: true
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
        trackerDao?.removeAllChangeListeners()
        realm.close()
    }

    class AttributeInformationViewModel(_attributeDAO: OTAttributeDAO, val realm: Realm) : RealmChangeListener<OTAttributeDAO> {
        var attributeDAO: OTAttributeDAO = _attributeDAO
            private set

        val isInDatabase: Boolean get() = attributeDAO.isManaged

        val nameObservable = BehaviorSubject.create<String>("")
        val typeObservable = BehaviorSubject.create<Int>(-1)
        val connectionObservable = BehaviorSubject.create<OTConnection>()

        var name: String
            get() = nameObservable.value
            set(value) {
                if (nameObservable.value != value) {
                    nameObservable.onNext(value)
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
        }

        override fun onChange(snapshot: OTAttributeDAO) {
            if (nameObservable.value != snapshot.name)
                nameObservable.onNext(snapshot.name)

            if (typeObservable.value != snapshot.type)
                typeObservable.onNext(snapshot.type)
        }

        fun applyChanges() {
            attributeDAO.name = nameObservable.value
            attributeDAO.type = typeObservable.value
            attributeDAO.updatedAt = System.currentTimeMillis()
        }

        fun saveToRealm() {
            this.attributeDAO = realm.copyToRealm(this.attributeDAO)
        }

        fun register() {
            if (isInDatabase)
                attributeDAO.addChangeListener(this)
        }
    }

    class AttributeViewModelListDiffUtilCallback(val oldList: List<AttributeInformationViewModel>, val newList: List<AttributeInformationViewModel>) : DiffUtil.Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] === newList[newItemPosition] ||
                    oldList[oldItemPosition].attributeDAO.objectId == newList[newItemPosition].attributeDAO.objectId
        }

        override fun getOldListSize(): Int {
            return oldList.size
        }

        override fun getNewListSize(): Int {
            return newList.size
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return areItemsTheSame(oldItemPosition, newItemPosition)
        }


    }
}