package kr.ac.snu.hcil.omnitrack.core.database

import android.support.annotation.Keep
import com.google.firebase.database.*
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.OTUser
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.backend.OTAuthManager
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTrigger
import rx.Observable
import java.util.*

/**
 * Created by younghokim on 2017. 2. 9..
 */
object FirebaseHelper {
    const val CHILD_NAME_USERS = "users"
    const val CHILD_NAME_TRACKERS = "trackers"
    const val CHILD_NAME_ATTRIBUTES = "attributes"
    const val CHILD_NAME_TRIGGERS = "triggers"
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
    class TriggerPOJO : NamedPOJO() {
        var user: String? = null
        var position: Int = 0
        var action: Int = 0
        var type: Int = 0
        var on: Boolean = false
        var properties: Map<String, String>? = null
        var lastTriggeredTime: Long = 0
    }

    @Keep
    class IndexedKey(
            var position: Int = 0,
            var key: String? = null) {
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
        val pojo = TriggerPOJO()
        pojo.action = trigger.action
        pojo.name = trigger.name
        pojo.on = trigger.isOn
        pojo.lastTriggeredTime = trigger.lastTriggeredTime
        pojo.position = position
        pojo.type = trigger.typeId
        pojo.user = userId
        val properties = HashMap<String, String>()
        trigger.writePropertiesToDatabase(properties)
        pojo.properties = properties

        val ref = triggerRef(triggerId = trigger.objectId)
        if (ref != null) {
            ref.setValue(pojo)
            ref.child("trackers").setValue(trigger.trackers.mapIndexed { i, tracker -> IndexedKey(i, tracker.objectId) })
        }
    }

    fun findTriggersOfUser(user: OTUser): Observable<List<OTTrigger>> {
        println("userId: ${user.objectId}")
        val query = //makeQueryOfUser(user.objectId, CHILD_NAME_TRIGGERS)
                dbRef?.child("triggers")?.orderByChild("user")?.equalTo(user.objectId)
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
                        println("Trigger snapshot exists ; ${snapshot.exists()}")
                        println("Trigger snapthot ${snapshot.value}")
                        val triggers = ArrayList<Pair<Int, OTTrigger>>(snapshot.childrenCount.toInt())

                        println("Trigger count: ${snapshot.childrenCount}")

                        for (child in snapshot.children) {
                            val pojo = child.getValue(TriggerPOJO::class.java)
                            if (pojo != null) {

                                val trackerIds = ArrayList<Pair<String, IndexedKey>>()
                                for (trackerIdRef in child.child("trackers").children) {
                                    val trackerIdWithPosition = trackerIdRef.getValue(IndexedKey::class.java)
                                    if (trackerIdWithPosition != null) {
                                        trackerIds.add(Pair(trackerIdRef.key, trackerIdWithPosition))
                                    }
                                }
                                trackerIds.sortBy { it -> it.second.position }


                                val trigger = OTTrigger.makeInstance(
                                        child.key,
                                        pojo.type,
                                        user,
                                        pojo.name ?: "",
                                        trackerIds.map { Pair<String?, String>(it.first, it.second.key!!) }.toTypedArray(),
                                        pojo.on, pojo.action, pojo.lastTriggeredTime, null)

                                triggers.add(
                                        Pair(pojo.position, trigger)
                                )
                            }
                        }

                        triggers.sortBy(Pair<Int, OTTrigger>::first)

                        if (!subscriber.isUnsubscribed) {
                            subscriber.onNext(triggers.map { it -> it.second })
                            subscriber.onCompleted()
                        }
                    }

                })

            }
        } else {
            Observable.error<List<OTTrigger>>(Exception("Firebase db error retrieving triggers."))
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

    fun makeQueryOfUser(userId: String, childName: String): Query? {
        return dbRef?.child(childName)?.orderByChild("user")?.equalTo(userId)
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

    fun findTrackersOfUser(userId: String): Observable<List<OTTracker>> {

        println("userId: ${userId}")


        val query = getContainsFlagListOfUser(userId, CHILD_NAME_TRACKERS)

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

                        println("Tracker snapshot exists ; ${snapshot.exists()}")
                        println("Tracker snapthot ${snapshot.value}")

                        val trackers = ArrayList<Pair<Int, OTTracker>>(snapshot.childrenCount.toInt())

                        println("Tracker count: ${snapshot.childrenCount}")

                        Observable.zip<List<OTTracker>>(snapshot.children.filter { it.value == true }.map {

                            Observable.create<Pair<Int, OTTracker>> {
                                subscriber ->
                                println("querying tracker ${it.key}")
                                val trackerRef = trackerRef(it.key)
                                if (trackerRef == null) {
                                    if (!subscriber.isUnsubscribed) {
                                        subscriber.onError(Exception("query does not exists."))
                                    }
                                } else {
                                    trackerRef.addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onCancelled(p0: DatabaseError?) {
                                            if (!subscriber.isUnsubscribed) {
                                                p0?.toException()?.printStackTrace()
                                                subscriber.onError(Exception("tracker query error."))
                                            }
                                        }

                                        override fun onDataChange(snapshot: DataSnapshot) {
                                            println(snapshot.value)
                                            if (snapshot.value != null) {
                                                if (!subscriber.isUnsubscribed) {
                                                    subscriber.onNext(extractTrackerWithPosition(snapshot))
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

                            pairs.map { it as Pair<Int, OTTracker> }.sortedBy { it.first }.map {
                                it.second
                            }
                        }).subscribe(subscriber)
                    }
                })

            }
        } else {
            Observable.error<List<OTTracker>>(Exception("Firebase db error retrieving trackers."))
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

        //deleteObjects(AttributeScheme, *tracker.fetchRemovedAttributeIds())

        /*
        for (child in tracker.attributes.iterator().withIndex()) {
            save(child.value, child.index)
        }*/
    }
}