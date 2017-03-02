package kr.ac.snu.hcil.omnitrack.core.database

import android.content.Intent
import android.support.annotation.Keep
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.database.*
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.core.OTItem
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.OTUser
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.backend.OTAuthManager
import kr.ac.snu.hcil.omnitrack.core.database.DatabaseHelper.Companion.SAVE_RESULT_EDIT
import kr.ac.snu.hcil.omnitrack.core.database.DatabaseHelper.Companion.SAVE_RESULT_NEW
import kr.ac.snu.hcil.omnitrack.core.datatypes.TimeSpan
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTrigger
import kr.ac.snu.hcil.omnitrack.utils.TimeHelper
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper
import rx.Observable
import rx.Single
import rx.subscriptions.Subscriptions
import java.io.Serializable
import java.util.*

/**
 * Created by younghokim on 2017. 2. 9..
 */
object FirebaseHelper {

    enum class Order {ASC, DESC }

    const val CHILD_NAME_USERS = "users"
    const val CHILD_NAME_TRACKERS = "trackers"
    const val CHILD_NAME_ATTRIBUTES = "attributes"
    const val CHILD_NAME_TRIGGERS = "triggers"
    const val CHILD_NAME_ITEMS = "items"
    const val CHILD_NAME_ATTRIBUTE_PROPERTIES = "properties"


    const val CHILD_NAME_EXPERIMENT_PROFILE = "experiment_profile"

    private val fbInstance: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance()
    }

    init {
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }

    val dbRef: DatabaseReference? get() = fbInstance.reference

    val currentUserRef: DatabaseReference? get() = dbRef?.child(CHILD_NAME_USERS)?.child(OTAuthManager.userId)

    val experimentProfileRef: DatabaseReference? get() = currentUserRef?.child(CHILD_NAME_EXPERIMENT_PROFILE)

    private fun trackerRef(trackerId: String): DatabaseReference? {
        return dbRef?.child(CHILD_NAME_TRACKERS)?.child(trackerId)
    }

    private fun triggerRef(triggerId: String): DatabaseReference? {
        return dbRef?.child(CHILD_NAME_TRIGGERS)?.child(triggerId)
    }

    open class NamedPOJO {
        var name: String? = null
    }

    @Keep class UserPOJO {
        var trackers: HashMap<String, Int>? = null
        var triggers: HashMap<String, Int>? = null
    }

    @Keep
    class TrackerPOJO : NamedPOJO() {
        var user: String? = null
        var position: Int = 0
        var color: Int = 0
        var attributeLocalKeySeed: Int = 0
        var onShortcut: Boolean = false
        var attributes: Map<String, AttributePOJO>? = null
    }

    @Keep
    class AttributePOJO : NamedPOJO() {
        var localKey: Int = -1
        var position: Int = 0
        var connectionSerialized: String? = null
        var type: Int = 0
        var required: Boolean = false
        var properties: Map<String, String>? = null
    }

    @Keep
    class MutableTriggerPOJO : NamedPOJO(), Serializable {
        var user: String? = null
        var position: Int = 0
        var action: Int = 0
        var type: Int = 0
        var on: Boolean = false
        val properties = HashMap<String, String>()
        var lastTriggeredTime: Long = 0
        var trackers = ArrayList<IndexedKey>()
    }

    @Keep
    class TriggerPOJO : NamedPOJO(), Serializable {
        var user: String? = null
        var position: Int = 0
        var action: Int = 0
        var type: Int = 0
        var on: Boolean = false
        var properties: Map<String, String>? = null
        var lastTriggeredTime: Long = 0
        var trackers: List<IndexedKey>? = null

        @Exclude
        fun toMutable(out: MutableTriggerPOJO?): MutableTriggerPOJO {
            val mutable = out ?: MutableTriggerPOJO()
            mutable.user = user
            mutable.position = position
            mutable.action = action
            mutable.type = type
            mutable.on = on

            if (properties != null)
                mutable.properties.putAll(properties!!)

            if (trackers != null)
                mutable.trackers.addAll(trackers!!)

            return mutable
        }
    }

    @Keep
    class ItemPOJO {
        var dataTable: Map<String, String>? = null
        var sourceType: Int = -1
        var timestamp: Any = 0

        @Exclude
        fun getTimestamp(): Long {
            if (timestamp is Int) {
                return (timestamp as Int).toLong()
            } else if (timestamp is Long) {
                return (timestamp as Long)
            } else throw Exception("Timestamp is not an integer of long.")
        }
    }

    @Keep
    class ItemListSummary {
        var totalCount: Long? = null
        var todayCount: Int? = null
        var lastLoggingTime: Long? = null
    }

    @Keep
    class DeviceInfo {
        var os: String? = "Android api-${android.os.Build.VERSION.SDK_INT}"
        var firstLoginAt: Any? = ServerValue.TIMESTAMP
    }

    @Keep
    class IndexedKey(
            var position: Int = 0,
            var key: String? = null) : Serializable {
    }

    fun generateNewKey(childName: String): String {
        val newKey = dbRef!!.child(childName).push().key
        println("New Firebase Key: ${newKey}")
        return newKey
    }

    fun generateAttributeKey(trackerId: String): String {
        return trackerRef(trackerId)!!.child(CHILD_NAME_ATTRIBUTES)!!.push().key
    }

    fun saveUser(user: OTUser) {
        for (child in user.trackers.iterator().withIndex()) {
            saveTracker(child.value, child.index)
        }

        for (triggerEntry in user.triggerManager.withIndex()) {
            saveTrigger(triggerEntry.value, user.objectId, triggerEntry.index)
        }
    }

    fun saveTrigger(trigger: OTTrigger, userId: String, position: Int) {
        val pojo = trigger.dumpDataToPojo(null)
        triggerRef(triggerId = trigger.objectId)?.setValue(pojo)
        setContainsFlagOfUser(userId, trigger.objectId, CHILD_NAME_TRIGGERS, true)
    }

    fun findTriggersOfUser(user: OTUser): Observable<List<OTTrigger>> {
        return findElementListOfUser(user.objectId, CHILD_NAME_TRIGGERS) {
            child ->
            extractTriggerWithPosition(user, child)
        }
    }

    fun getTrigger(user: OTUser, key: String): Observable<OTTrigger> {
        return Observable.create {
            subscriber ->
            val query = dbRef?.child(CHILD_NAME_TRIGGERS)?.child(key)
            if (query != null) {
                query.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(Exception("Firebase query failed"))
                        }
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        val triggerWithPosition = extractTriggerWithPosition(user, snapshot)
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onNext(triggerWithPosition.second)
                            subscriber.onCompleted()
                        }
                    }

                })
            } else {
                if (!subscriber.isUnsubscribed) {
                    subscriber.onError(NullPointerException("Firebase query is null"))
                }
            }
        }
    }

    fun extractTriggerWithPosition(user: OTUser, snapshot: DataSnapshot): Pair<Int, OTTrigger> {
        val pojo = snapshot.getValue(TriggerPOJO::class.java)
        if (pojo.user == user.objectId) {
            val trigger = OTTrigger.makeInstance(
                    snapshot.key,
                    pojo.type,
                    user,
                    pojo.name ?: "",
                    pojo.trackers?.map { it.key!! }?.toTypedArray(),
                    pojo.on, pojo.action, pojo.lastTriggeredTime, pojo.properties)

            return Pair(pojo.position, trigger)
        } else {
            throw IllegalArgumentException("user is different.")
        }
    }

    fun getContainsFlagListOfUser(userId: String, childName: String): DatabaseReference? {
        return dbRef?.child(CHILD_NAME_USERS)?.child(userId)?.child(childName)
    }

    fun setContainsFlagOfUser(userId: String, objectId: String, childName: String, contains: Boolean) {
        getContainsFlagListOfUser(userId, childName)?.child(objectId)?.setValue(if (contains) {
            true
        } else {
            null
        })
    }

    fun removeTracker(tracker: OTTracker, formerOwner: OTUser) {
        println("Firebase remove tracker: ${tracker.name}, ${tracker.objectId}")
        deBelongReference(trackerRef(tracker.objectId), CHILD_NAME_TRACKERS, formerOwner.objectId)
    }

    fun removeTrigger(trigger: OTTrigger) {

        println("Firebase remove tracker: ${trigger.name}, ${trigger.objectId}")
        deBelongReference(triggerRef(trigger.objectId), CHILD_NAME_TRIGGERS, trigger.user.objectId)
    }

    private fun deBelongReference(ref: DatabaseReference?, childName: String, userId: String) {
        if (ref != null) {
            setContainsFlagOfUser(userId, ref.key, childName, false)

            ref.child("removed_at")?.setValue(ServerValue.TIMESTAMP)
            ref.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError?) {

                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    val trashRef = dbRef?.child("removed")?.child(childName)?.child(snapshot.key)
                    trashRef?.setValue(snapshot.value)
                    snapshot.ref.removeValue()
                }

            })
        }
    }

    fun removeAttribute(trackerId: String, objectId: String) {
        trackerRef(trackerId)?.child(CHILD_NAME_ATTRIBUTES)?.child(objectId)?.removeValue()
    }

    fun extractTrackerWithPosition(snapshot: DataSnapshot): Pair<Int, OTTracker> {
        val pojo = snapshot.getValue(TrackerPOJO::class.java)
        //val attributesRef = snapshot.child(CHILD_NAME_ATTRIBUTES)

        val attributes = pojo.attributes
        var attributeList: ArrayList<OTAttribute<out Any>>? = null
        if (attributes != null) {
            val attrPojos = ArrayList<Map.Entry<String, AttributePOJO>>(pojo.attributes?.entries)

            attrPojos.sortBy { it -> it.value.position }

            attributeList = attrPojos.mapTo(ArrayList<OTAttribute<out Any>>()) {
                OTAttribute.createAttribute(
                        it.key,
                        it.value.localKey,
                        null,
                        it.value.name ?: "noname",
                        it.value.required,
                        it.value.type,
                        it.value.properties,
                        it.value.connectionSerialized)
            }
        }

        return Pair(pojo.position, OTTracker(
                snapshot.key,
                pojo.name ?: "Noname",
                pojo.color,
                pojo.onShortcut,
                pojo.attributeLocalKeySeed,
                attributeList
        ))
    }

    fun <T> findElementListOfUser(userId: String, childName: String, extractFunc: (DataSnapshot) -> Pair<Int, T>): Observable<List<T>> {
        val query = getContainsFlagListOfUser(userId, childName)
        return if (query != null) {
            Observable.create {
                subscriber ->
                query.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        error.toException().printStackTrace()
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(error.toException())
                        }
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {

                        println("$childName snapshot exists ; ${snapshot.exists()}")
                        println("$childName snapthot ${snapshot.value}")
                        println("$childName count: ${snapshot.childrenCount}")

                        if (!snapshot.exists()) {
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onNext(emptyList())
                                subscriber.onCompleted()
                                return
                            }
                        }

                        Observable.zip<List<T>>(snapshot.children.filter { it.value == true }.map {

                            Observable.create<Pair<Int, T>> {
                                subscriber ->
                                val ref = dbRef?.child(childName)?.child(it.key)
                                if (ref == null) {
                                    if (!subscriber.isUnsubscribed) {
                                        subscriber.onError(Exception("query does not exists."))
                                    }
                                } else {
                                    ref.addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onCancelled(p0: DatabaseError?) {
                                            if (!subscriber.isUnsubscribed) {
                                                p0?.toException()?.printStackTrace()
                                                subscriber.onError(Exception("$childName query error."))
                                            }
                                        }

                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            println(snapshot.value)
                                            if (snapshot.value != null) {
                                                if (!subscriber.isUnsubscribed) {
                                                    subscriber.onNext(extractFunc(snapshot))
                                                    subscriber.onCompleted()
                                                }
                                            } else {
                                                if (!subscriber.isUnsubscribed) {
                                                    subscriber.onError(Exception("tracker id does not exists."))
                                                }
                                            }
                                        }

                                    })
                                }
                            }.onErrorResumeNext { Observable.empty() }


                        }, {
                            pairs ->

                            pairs.map { it as Pair<Int, T> }.sortedBy { it.first }.map {
                                it.second
                            }
                        }).subscribe(subscriber)
                    }
                })

            }
        } else {
            Observable.error<List<T>>(Exception("Firebase db error retrieving $childName."))
        }

    }

    fun findTrackersOfUser(userId: String): Observable<List<OTTracker>> {
        return findElementListOfUser(userId, CHILD_NAME_TRACKERS) {
            snapshot ->
            extractTrackerWithPosition(snapshot)
        }
    }

    fun getTracker(key: String): Observable<OTTracker> {
        return Observable.create {
            subscriber ->
            val query = dbRef?.child(CHILD_NAME_TRACKERS)?.child(key)
            if (query != null) {
                query.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(Exception("Firebase query failed"))
                        }
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        val trackerWithPosition = extractTrackerWithPosition(snapshot)
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onNext(trackerWithPosition.second)
                            subscriber.onCompleted()
                        }
                    }

                })
            } else {
                if (!subscriber.isUnsubscribed) {
                    subscriber.onError(NullPointerException("Firebase query is null"))
                }
            }
        }
    }

    fun saveAttribute(trackerId: String, attribute: OTAttribute<out Any>, position: Int) {
        val attributeRef = trackerRef(trackerId)?.child("attributes")?.child(attribute.objectId)
        if (attributeRef != null) {
            val pojo = makeAttributePojo(attribute, position)
            attributeRef.setValue(pojo)
        }
    }

    private fun makeAttributePojo(attribute: OTAttribute<out Any>, position: Int): AttributePOJO {
        val pojo = AttributePOJO()
        pojo.localKey = attribute.localKey
        pojo.position = position
        pojo.required = attribute.isRequired
        pojo.connectionSerialized = attribute.valueConnection?.getSerializedString()
        pojo.type = attribute.typeId
        pojo.name = attribute.name
        val properties = HashMap<String, String>()
        attribute.writePropertiesToDatabase(properties)
        pojo.properties = properties

        return pojo
    }

    fun saveTracker(tracker: OTTracker, position: Int) {
        println("save tracker: ${tracker.name}, ${tracker.objectId}")

        if (tracker.owner != null) {
            FirebaseHelper.setContainsFlagOfUser(tracker.owner!!.objectId, tracker.objectId, FirebaseHelper.CHILD_NAME_TRACKERS, true)
        }
        val values = TrackerPOJO()
        values.name = tracker.name
        values.position = position
        values.color = tracker.color
        values.user = tracker.owner?.objectId
        values.onShortcut = tracker.isOnShortcut
        values.attributeLocalKeySeed = tracker.attributeLocalKeySeed

        val attributes = HashMap<String, AttributePOJO>()
        for (attribute in tracker.attributes.unObservedList.withIndex()) {
            attributes[attribute.value.objectId] = makeAttributePojo(attribute.value, attribute.index)
        }
        values.attributes = attributes

        val trackerRef = trackerRef(tracker.objectId)

        trackerRef?.setValue(values, DatabaseReference.CompletionListener { p0, p1 ->
            if (p0 != null) {
                p0.toException().printStackTrace()
                println("Firebase error.")
            } else {
                println("No firebase error. completed.")
            }
        })
    }

    fun getItemListContainerOfTrackerChild(trackerId: String): DatabaseReference? {
        return dbRef?.child(CHILD_NAME_ITEMS)?.child(trackerId)
    }

    fun getItemListOfTrackerChild(trackerId: String): DatabaseReference? {
        return getItemListContainerOfTrackerChild(trackerId)?.child("list")
    }

    fun getItemStatisticsOfTrackerChild(trackerId: String): DatabaseReference? {
        return getItemListContainerOfTrackerChild(trackerId)?.child("statistics")
    }

    fun removeItem(item: OTItem) {
        //deleteObjects(DatabaseHelper.ItemScheme, item.objectId!!)
        val itemId = item.objectId
        if (itemId != null) {
            getItemListOfTrackerChild(item.trackerObjectId)?.child(itemId)?.removeValue { databaseError, databaseReference ->
                if (databaseError == null) {
                    val intent = Intent(OTApplication.BROADCAST_ACTION_ITEM_REMOVED)

                    intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER, item.trackerObjectId)

                    OTApplication.app.sendBroadcast(intent)
                }
            }
        }
    }

    fun getItem(tracker: OTTracker, itemId: String): Observable<OTItem> {
        val ref = getItemListOfTrackerChild(tracker.objectId)?.child(itemId)
        if (ref != null) {
            return Observable.create {
                subscriber ->


                ref.addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(error.toException())
                        }
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (!subscriber.isUnsubscribed) {
                            val pojo = snapshot.getValue(ItemPOJO::class.java)
                            if (pojo != null) {
                                val item = OTItem(snapshot.key, tracker.objectId, pojo.dataTable, pojo.getTimestamp(), OTItem.LoggingSource.values()[pojo.sourceType])
                                subscriber.onNext(item)
                                subscriber.onCompleted()
                            } else {
                                subscriber.onError(Exception("Database parse error"))
                            }
                        }
                    }
                })

            }
        } else {
            return Observable.error(Exception("No database reference for Item ${itemId} of tracker ${tracker.objectId}"))
        }
    }

    fun loadItems(tracker: OTTracker, timeRange: TimeSpan? = null, order: Order = Order.DESC): Observable<List<OTItem>> {
        val ref = getItemListOfTrackerChild(tracker.objectId)
        if (ref != null) {
            var query = ref.orderByChild("timestamp")
            return Observable.create { subscriber ->
                if (timeRange != null) {
                    query = query.startAt(timeRange.from.toDouble(), "timestamp").endAt(timeRange.to.toDouble(), "timestamp")
                }

                val valueChangedListener = object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        if (subscriber.isUnsubscribed) {
                            query.removeEventListener(this)
                        }

                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(error.toException())
                        }
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (subscriber.isUnsubscribed) {
                            println("auto unsubscribe")
                            query.removeEventListener(this)
                        }

                        if (snapshot.exists()) {
                            if (!subscriber.isUnsubscribed) {
                                val list = snapshot.children.mapTo(ArrayList<OTItem>()) {
                                    val pojo = it.getValue(ItemPOJO::class.java)
                                    OTItem(it.key, tracker.objectId, pojo.dataTable, pojo.getTimestamp(), OTItem.LoggingSource.values()[pojo.sourceType])
                                }

                                list.sortBy {
                                    when (order) {
                                        Order.ASC -> it.timestamp
                                        else -> -it.timestamp
                                    }
                                }
                                subscriber.onNext(list)
                            }
                        } else {
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onNext(emptyList())
                            }
                        }
                    }

                }

                query.addValueEventListener(valueChangedListener)

                subscriber.add(Subscriptions.create { query.removeEventListener(valueChangedListener) })
            }
        } else return Observable.error(Exception("No reference"))

    }

    fun getLogCountDuring(tracker: OTTracker, from: Long, to: Long): Observable<Long> {
        return Observable.create {
            subscriber ->
            getItemListOfTrackerChild(tracker.objectId)?.orderByChild("timestamp")?.startAt(from.toDouble(), "timestamp")?.endAt(to.toDouble(), "timestamp")
                    ?.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                            p0.toException().printStackTrace()
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onError(p0.toException())
                            }
                        }

                        override fun onDataChange(snapshot: DataSnapshot) {
                            println("item count: ${snapshot.childrenCount}")
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onNext(snapshot.childrenCount)
                                subscriber.onCompleted()
                            }
                        }
                    })
        }
        /*
        val numRows = DatabaseUtils.queryNumEntries(readableDatabase, ItemScheme.tableName, "${ItemScheme.TRACKER_ID}=? AND ${ItemScheme.LOGGED_AT} BETWEEN ? AND ?", arrayOf(tracker.objectId.toString(), from.toString(), to.toString()))
        return numRows.toInt()*/
    }

    fun getLogCountOfDay(tracker: OTTracker): Observable<Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val first = cal.timeInMillis

        cal.add(Calendar.DAY_OF_YEAR, 1)
        val second = cal.timeInMillis - 20

        return getLogCountDuring(tracker, first, second)
    }

    fun getTotalItemCount(tracker: OTTracker): Observable<Long> {
        return Observable.create {
            subscriber ->
            getItemListOfTrackerChild(tracker.objectId)
                    ?.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                            p0.toException().printStackTrace()
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onError(p0.toException())
                            }
                        }

                        override fun onDataChange(snapshot: DataSnapshot) {
                            println("item count: ${snapshot.childrenCount}")
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onNext(snapshot.childrenCount)
                                subscriber.onCompleted()
                            }
                        }
                    })
        }
    }

    fun getLastLoggingTime(tracker: OTTracker): Observable<Long?> {
        return Observable.create {
            subscriber ->
            getItemListOfTrackerChild(tracker.objectId)?.orderByChild("timestamp")?.limitToLast(1)
                    ?.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onCancelled(p0: DatabaseError) {
                            p0.toException().printStackTrace()
                            if (!subscriber.isUnsubscribed) {
                                subscriber.onError(p0.toException())
                            }
                        }

                        override fun onDataChange(snapshot: DataSnapshot) {
                            println("latest log count: ${snapshot.childrenCount}")
                            if (!subscriber.isUnsubscribed) {
                                if (snapshot.exists()) {
                                    val timestamp = snapshot.children.last().child("timestamp").value
                                    if (timestamp is Long) {
                                        subscriber.onNext(timestamp)
                                    } else if (timestamp is Int) {
                                        subscriber.onNext(timestamp.toLong())
                                    } else subscriber.onNext(null)
                                    subscriber.onCompleted()
                                } else {
                                    subscriber.onNext(null)
                                    subscriber.onCompleted()
                                }
                            }
                        }
                    })
        }
    }

    fun getItemListSummary(tracker: OTTracker): Observable<ItemListSummary> {
        return Observable.create {
            subscriber ->

            val eventListener = object : ValueEventListener {
                override fun onCancelled(p0: DatabaseError) {
                    p0.toException().printStackTrace()
                    if (!subscriber.isUnsubscribed) {
                        subscriber.onError(p0.toException())
                    }
                }

                override fun onDataChange(snapshot: DataSnapshot) {
                    println("item count: ${snapshot.childrenCount}")
                    if (!subscriber.isUnsubscribed) {
                        val info = ItemListSummary()
                        info.totalCount = snapshot.childrenCount
                        if (snapshot.hasChildren()) {
                            val lastLoggingTime = snapshot.children.last().child("timestamp").value
                            if (lastLoggingTime is Long) info.lastLoggingTime = lastLoggingTime
                            else if (lastLoggingTime is Int) info.lastLoggingTime = lastLoggingTime.toLong()

                            val startOfToday = TimeHelper.cutTimePartFromEpoch(System.currentTimeMillis())
                            info.todayCount = snapshot.children.filter {
                                val timestamp = it.child("timestamp").value
                                if (timestamp is Long) {
                                    timestamp >= startOfToday
                                } else if (timestamp is Int) {
                                    timestamp >= startOfToday.toInt()
                                } else false
                            }.count()
                        }

                        subscriber.onNext(info)
                        subscriber.onCompleted()
                    }
                }
            }

            val query = getItemListOfTrackerChild(tracker.objectId)?.orderByChild("timestamp")

            query?.addListenerForSingleValueEvent(eventListener)

            subscriber.add(Subscriptions.create {
                query?.removeEventListener(eventListener)
            })
        }
    }

    fun saveItem(item: OTItem, tracker: OTTracker, notifyIntent: Boolean = true) {
        val pojo = ItemPOJO()
        pojo.timestamp = if (item.timestamp != -1L) {
            item.timestamp
        } else {
            ServerValue.TIMESTAMP
        }
        pojo.sourceType = item.source.ordinal
        val data = HashMap<String, String>()

        for (attribute in tracker.attributes) {
            val value = item.getCastedValueOf(attribute)
            if (value != null) {
                data[attribute.objectId] = TypeStringSerializationHelper.serialize(attribute.typeNameForSerialization, value)
            }
        }

        println("store data ${data}")

        pojo.dataTable = data

        val result = if (item.objectId != null) {
            SAVE_RESULT_EDIT
        } else {
            SAVE_RESULT_NEW
        }

        val itemRef = if (item.objectId != null) {
            getItemListOfTrackerChild(tracker.objectId)?.child(item.objectId!!)
        } else getItemListOfTrackerChild(tracker.objectId)?.push()

        itemRef?.setValue(pojo)?.addOnCompleteListener(object : OnCompleteListener<Void> {
            override fun onComplete(task: Task<Void>) {
                if (task.isSuccessful) {
                    if (item.objectId == null)
                        item.objectId = itemRef.key

                    if (notifyIntent) {
                        val intent = Intent(when (result) {
                            SAVE_RESULT_NEW -> OTApplication.BROADCAST_ACTION_ITEM_ADDED
                            SAVE_RESULT_EDIT -> OTApplication.BROADCAST_ACTION_ITEM_EDITED
                            else -> throw IllegalArgumentException("")
                        })

                        intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER, tracker.objectId)
                        intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_ITEM, item.objectId)

                        OTApplication.app.sendBroadcast(intent)
                    }
                }
            }

        })

        /*
        * val values = ContentValues()

        //values.put(ItemScheme.TRACKER_ID, tracker.objectId)
        values.put(ItemScheme.SOURCE_TYPE, item.source.ordinal)
        values.put(ItemScheme.VALUES_JSON, item.getSerializedValueTable(tracker))
        if (item.timestamp != -1L) {
            values.put(ItemScheme.LOGGED_AT, item.timestamp)
        }

        println("item id: ${item.objectId}")

        val result = saveObject(item, values, ItemScheme)

        if(result != SAVE_RESULT_FAIL) {
            if(notifyIntent) {
                val intent = Intent(when (result) {
                    SAVE_RESULT_NEW -> OTApplication.BROADCAST_ACTION_ITEM_ADDED
                    SAVE_RESULT_EDIT -> OTApplication.BROADCAST_ACTION_ITEM_EDITED
                    else -> throw IllegalArgumentException("")
                })

                intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER, tracker.objectId)
                intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_ITEM, item.objectId)

                OTApplication.app.sendBroadcast(intent)
            }
            return result
        }
        else{
            println("Item insert failed - $item")
            return result
        }*/

    }

    fun checkHasDeviceId(userId: String, deviceId: String): Single<Boolean> {
        val query = FirebaseHelper.dbRef?.child(FirebaseHelper.CHILD_NAME_USERS)?.child(userId)?.child("devices")?.child(deviceId)
        if (query != null) {
            return Single.create {
                subscriber ->
                val listener = object : ValueEventListener {
                    override fun onCancelled(error: DatabaseError) {
                        if (!subscriber.isUnsubscribed) {
                            subscriber.onError(error.toException())
                        }
                    }

                    override fun onDataChange(snapshot: DataSnapshot) {
                        subscriber.onSuccess(snapshot.exists())
                    }

                }
                query.addListenerForSingleValueEvent(listener)
                subscriber.add(Subscriptions.create { query.removeEventListener(listener) })
            }
        } else {
            return Single.error(Exception("Database reference does not exist."))
        }
    }

    fun addDeviceInfoToUser(userId: String, deviceId: String): Single<DeviceInfo> {
        val query = FirebaseHelper.dbRef?.child(FirebaseHelper.CHILD_NAME_USERS)?.child(userId)?.child("devices")
        if (query != null) {
            return Single.create {
                subscriber ->

                val info = DeviceInfo()
                query.child(deviceId).setValue(info).addOnCompleteListener({
                    result ->
                    if (!subscriber.isUnsubscribed) {
                        if (result.isSuccessful) {
                            subscriber.onSuccess(info)
                        } else {
                            subscriber.onError(result.exception)
                        }
                    }
                })
            }
        } else {
            return Single.error(Exception("Database reference does not exist."))
        }
    }

    fun removeDeviceInfo(userId: String, deviceId: String): Single<Boolean> {
        val query = FirebaseHelper.dbRef?.child(FirebaseHelper.CHILD_NAME_USERS)?.child(userId)?.child("devices")
        if (query != null) {
            return Single.create {
                subscriber ->
                query.child(deviceId).removeValue().addOnCompleteListener({
                    result ->
                    if (!subscriber.isUnsubscribed) {
                        if (result.isSuccessful) {
                            subscriber.onSuccess(true)
                        } else {
                            subscriber.onError(result.exception)
                        }
                    }
                })
            }
        } else {
            return Single.error(Exception("Database reference does not exist."))
        }
    }
}