package kr.ac.snu.hcil.omnitrack.core

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.database.FirebaseHelper
import kr.ac.snu.hcil.omnitrack.core.system.OTShortcutPanelManager
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTrigger
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTriggerManager
import kr.ac.snu.hcil.omnitrack.utils.DefaultNameGenerator
import kr.ac.snu.hcil.omnitrack.utils.ObservableList
import kr.ac.snu.hcil.omnitrack.utils.ReadOnlyPair
import rx.Observable
import rx.internal.util.SubscriptionList
import rx.subjects.PublishSubject
import rx.subjects.SerializedSubject
import java.util.*

/**
 * Created by Young-Ho Kim on 2016-07-11.
 */
class OTUser(val objectId: String, var name: String?, var photoUrl: String?, _trackers: List<OTTracker>? = null) {

    companion object {

        const val PREFERENCES_KEY_OBJECT_ID = "ot_user_object_id"
        const val PREFERENCES_KEY_NAME = "ot_user_name"
        const val PREFERENCES_KEY_PHOTO_URL = "ot_user_photo_url"


        //const val PREFERENCES_KEY_EMAIL = "user_email"

        fun isUserStored(sp: SharedPreferences): Boolean {
            return sp.contains(PREFERENCES_KEY_OBJECT_ID)
        }

        fun loadCachedInstance(sp: SharedPreferences): rx.Observable<OTUser> {
            if (isUserStored(sp)) {
                val objId = sp.getString(PREFERENCES_KEY_OBJECT_ID, null)
                val name = sp.getString(PREFERENCES_KEY_NAME, null)
                val photoUrl = sp.getString(PREFERENCES_KEY_PHOTO_URL, null)
                if (objId != null && name != null) {
                    return FirebaseHelper.findTrackersOfUser(objId).flatMap {
                        trackers ->
                        val user = OTUser(objId, name, photoUrl, trackers)
                        FirebaseHelper.findTriggersOfUser(user).map {
                            triggers ->
                            for (trigger in triggers) {
                                user.triggerManager.putNewTrigger(trigger)
                            }
                            user
                        }
                    }
                } else return Observable.error(Exception("UserInfo is not stored"))
            } else return Observable.error(Exception("User is not stored"))
        }

        fun storeOrOverwriteInstanceCache(instance: OTUser, sp: SharedPreferences): Boolean {
            val overwritten = sp.contains(PREFERENCES_KEY_OBJECT_ID)

            sp.edit()
                    .putString(PREFERENCES_KEY_OBJECT_ID, instance.objectId)
                    .putString(PREFERENCES_KEY_NAME, instance.name)
                    .putString(PREFERENCES_KEY_PHOTO_URL, instance.photoUrl)
                    .apply()

            return overwritten
        }

        fun clearInstanceCache(sp: SharedPreferences): Boolean {
            val removed = sp.contains(PREFERENCES_KEY_OBJECT_ID)

            sp.edit()
                    .remove(PREFERENCES_KEY_OBJECT_ID)
                    .remove(PREFERENCES_KEY_NAME)
                    .remove(PREFERENCES_KEY_PHOTO_URL)
                    .apply()

            return removed
        }
    }

    var isDatasetDirtySinceLastSync = false


    /*
    val email: String by Delegates.observable(email) {
        prop, old, new ->
        if (old != new) {
            isDirtySinceLastSync = true
        }
    }*/

    private val databaseRef: DatabaseReference?
        get() = FirebaseHelper.dbRef?.child(FirebaseHelper.CHILD_NAME_USERS)?.child(objectId)

    val trackers = ObservableList<OTTracker>()


    val triggerManager: OTTriggerManager

    //val trackerAdded = Event<ReadOnlyPair<OTTracker, Int>>()

    private val subscriptions = SubscriptionList()

    val trackerAdded = SerializedSubject(PublishSubject.create<ReadOnlyPair<OTTracker, Int>>())
    val trackerRemoved = SerializedSubject(PublishSubject.create<ReadOnlyPair<OTTracker, Int>>())
    val trackerIndexChanged = SerializedSubject(PublishSubject.create<ReadOnlyPair<OTTracker, Int>>())

    private var trackerListDbReference: DatabaseReference? = null
    private val trackerListChangeEventListener: ChildEventListener

    private var triggerListDbReference: DatabaseReference? = null
    private val triggerListChangeEventListener: ChildEventListener


    init {

        if (_trackers != null) {
            for (tracker: OTTracker in _trackers) {
                trackers.unObservedList.add(tracker)

                tracker.addedToUser.suspend = true
                tracker.owner = this
                tracker.addedToUser.suspend = false

            }
        }

        triggerManager = OTTriggerManager(this)

        trackers.elementAdded += { sender, args ->
            onTrackerAdded(args.first, args.second)
        }

        trackers.elementRemoved += { sender, args ->
            onTrackerRemoved(args.first, args.second)
        }

        trackers.elementReordered += {
            sender, range ->
            for (i in range) {
                trackers[i].databasePointRef?.child("position")?.setValue(i)
            }
        }

        trackerListDbReference = databaseRef?.child(FirebaseHelper.CHILD_NAME_TRACKERS)
        triggerListDbReference = databaseRef?.child(FirebaseHelper.CHILD_NAME_TRIGGERS)


        trackerListChangeEventListener = object : ChildEventListener {
            override fun onCancelled(error: DatabaseError) {
                println("trackers error: ${error.toException().printStackTrace()}")
            }

            override fun onChildAdded(snapshot: DataSnapshot, previousChildKey: String?) {
                println("tracker child added : ${snapshot.key}")
                val duplicate = trackers.unObservedList.find { it.objectId == snapshot.key }
                if (duplicate == null) {
                    println("load tracker ${snapshot.key} from DB")
                    FirebaseHelper.getTracker(snapshot.key).subscribe {
                        tracker ->
                        tracker.owner = this@OTUser
                        this@OTUser.trackers += tracker
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildKey: String?) {
                println("tracker child changed : ${snapshot.key}")
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildKey: String?) {

                println("tracker child moved : ${snapshot.key}")
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                println("tracker child removed: ${snapshot.key}")
                val duplicate = trackers.unObservedList.find { it.objectId == snapshot.key }
                if (duplicate != null) {
                    duplicate.dispose()
                    trackers.remove(duplicate)
                }
            }

        }

        triggerListChangeEventListener = object : ChildEventListener {
            override fun onCancelled(error: DatabaseError) {
                println("triggers error: ${error.toException().printStackTrace()}")
            }

            override fun onChildAdded(snapshot: DataSnapshot, previousChildKey: String?) {
                println("trigger child added : ${snapshot.key}")
                val duplicate = triggerManager.getTriggerWithId(snapshot.key)
                if (duplicate == null) {
                    println("load trigger ${snapshot.key} from DB")
                    FirebaseHelper.getTrigger(this@OTUser, snapshot.key).subscribe {
                        trigger ->
                        triggerManager.putNewTrigger(trigger)
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildKey: String?) {
                println("trigger child changed : ${snapshot.key}")
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildKey: String?) {

                println("trigger child moved : ${snapshot.key}")
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                println("trigger child removed: ${snapshot.key}")
                val duplicate = triggerManager.getTriggerWithId(snapshot.key)
                if (duplicate != null) {
                    triggerManager.removeTrigger(duplicate)
                }
            }

        }

        trackerListDbReference?.addChildEventListener(trackerListChangeEventListener)
        triggerListDbReference?.addChildEventListener(triggerListChangeEventListener)


    }

    fun getTrackersOnShortcut(): List<OTTracker>{
        return trackers.filter { it.isOnShortcut == true }
    }

    /*
    fun getRecentTrackers(): List<OTTracker>{
        val list = trackers.unObservedList.toMutableList()
        list.map { OTApplication.app.dbHelper.getLastLoggingTimeAsync(it,) }
    }*/

    fun detachFromSystem() {
        triggerManager.detachFromSystem()
        trackerListDbReference?.removeEventListener(trackerListChangeEventListener)
        triggerListDbReference?.removeEventListener(triggerListChangeEventListener)
        subscriptions.clear()
    }

    fun newTrackerWithDefaultName(context: Context, add: Boolean): OTTracker {
        return newTracker(this.generateNewTrackerName(context), add)
    }

    fun newTracker(name: String, add: Boolean): OTTracker {
        val tracker = OTTracker(name)
        val unOccupied = OTApplication.app.colorPalette.filter {
            color ->
            trackers.unObservedList.find {
                tracker ->
                tracker.color == color
            } == null
        }

        tracker.color = if (unOccupied.isNotEmpty()) {
            unOccupied.first()
        } else {
            OTApplication.app.colorPalette.first()
        }

        if (add) {
            trackers.add(tracker)
        }

        return tracker
    }

    fun getPermissionsRequiredForFields(): Set<String> {
        val set = HashSet<String>()
        for (tracker in trackers) {
            for (attr in tracker.attributes) {
                val permissions = attr.requiredPermissions()
                if (permissions != null) {
                    set.addAll(permissions)
                }
            }
        }

        return set
    }

    private fun onTrackerAdded(new: OTTracker, index: Int) {
        new.owner = this
        if (new.isOnShortcut) {
            OTShortcutPanelManager += new
        }

        trackerAdded.onNext(ReadOnlyPair(new, index))

        println("tracker was added")
        FirebaseHelper.saveTracker(new, index)
    }

    private fun onTrackerRemoved(tracker: OTTracker, index: Int) {
        tracker.owner = null

        trackerRemoved.onNext(ReadOnlyPair(tracker, index))

        tracker.suspendDatabaseSync = true
        if (tracker.isOnShortcut) {
            tracker.isOnShortcut = false
        }

        //TODO currently, reminders are assigned to tracker so should be removed.
        val reminders = triggerManager.getAttachedTriggers(tracker, OTTrigger.ACTION_NOTIFICATION)
        for (reminder in reminders) {
            reminder.isOn = false
            triggerManager.removeTrigger(reminder)
        }

        println("tracker was removed.")
        tracker.suspendDatabaseSync = false
        FirebaseHelper.removeTracker(tracker, this)
    }

    fun findAttributeByObjectId(trackerId: String, attributeId: String): OTAttribute<out Any>? {
        val tracker = get(trackerId)

        return get(trackerId)?.attributes?.unObservedList?.find { it.objectId == attributeId }
    }

    operator fun get(trackerId: String): OTTracker? {
        return trackers.unObservedList.find { it.objectId == trackerId }
    }

    fun generateNewTrackerName(context: Context): String {
        return DefaultNameGenerator.generateName(context.resources.getString(R.string.msg_new_tracker_prefix), trackers.unObservedList.map { it.name }, false)
    }
}