package kr.ac.snu.hcil.omnitrack.core.triggers

import android.widget.Toast
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.core.OTNotificationManager
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.OTUser
import kr.ac.snu.hcil.omnitrack.services.OTBackgroundLoggingService
import java.util.*

/**
 * Created by younghokim on 16. 7. 28..
 */
class OTTriggerManager(val user: OTUser) {

    init {
        user.trackerAdded += {
            sender, args ->

        }

        user.trackerRemoved += {
            sender, args ->
            for (trigger in getAttachedTriggers(args.first).clone()) {
                removeTrigger(trigger)
            }
        }
    }

    constructor(user: OTUser, loadedTriggers: List<OTTrigger>?) : this(user) {

        if (loadedTriggers != null) {
            triggers += loadedTriggers

            triggers.forEach {
                it.fired += triggerFiredHandler
                it.activateOnSystem(OTApplication.app.applicationContext)
            }
        }
    }


    private val _removedTriggerIds = ArrayList<Long>()
    fun fetchRemovedTriggerIds(): LongArray {
        val result = _removedTriggerIds.toLongArray()
        _removedTriggerIds.clear()
        return result;
    }

    private val triggers = ArrayList<OTTrigger>()

    private val trackerPivotedTriggerListCache = Hashtable<String, Array<OTTrigger>>()

    private val triggerFiredHandler = {
        sender: Any, triggerTime: Long ->
        onTriggerFired(sender as OTTrigger, triggerTime)
    }

    fun getTriggerWithId(objId: String): OTTrigger? {
        return triggers.find { it.objectId == objId }
    }

    fun getAttachedTriggers(tracker: OTTracker): Array<OTTrigger> {
        if (trackerPivotedTriggerListCache.containsKey(tracker.objectId)) {
            //println("triggers cache hit")
        } else {
            //println("triggers cache miss")
            trackerPivotedTriggerListCache.set(tracker.objectId,
                    triggers.filter { it.trackerObjectId == tracker.objectId }.toTypedArray())
        }

        return trackerPivotedTriggerListCache[tracker.objectId]!!
    }

    fun putNewTrigger(trigger: OTTrigger) {
        if (triggers.find { it.objectId == trigger.objectId } == null) {
            triggers.add(trigger)
            trigger.fired += triggerFiredHandler

            if (trigger.isOn) {
                trigger.activateOnSystem(OTApplication.app.applicationContext)
            }

            if (_removedTriggerIds.contains(trigger.dbId)) {
                _removedTriggerIds.remove(trigger.dbId)
            }

            if (trackerPivotedTriggerListCache.containsKey(trigger.trackerObjectId)) {
                trackerPivotedTriggerListCache[trigger.trackerObjectId] =
                        trackerPivotedTriggerListCache[trigger.trackerObjectId]!! + trigger
            }
        }
    }

    fun removeTrigger(trigger: OTTrigger) {
        triggers.remove(trigger)
        trigger.fired -= triggerFiredHandler

        trigger.isOn = false

        if (trigger.dbId != null)
            _removedTriggerIds.add(trigger.dbId!!)


        //TODO handler dependencies associated with the trigger
        if (trigger is OTTimeTrigger) {
            OTTimeTriggerAlarmManager.cancelTrigger(trigger)
        }


        if (trackerPivotedTriggerListCache.containsKey(trigger.trackerObjectId)) {
            trackerPivotedTriggerListCache[trigger.trackerObjectId] = trackerPivotedTriggerListCache[trigger.trackerObjectId]!!.filter { it != trigger }.toTypedArray()
        }
    }

    private fun onTriggerFired(trigger: OTTrigger, triggerTime: Long) {
        when (trigger.action) {
            OTTrigger.ACTION_BACKGROUND_LOGGING -> {
                println("trigger fired - loggin in background")

                Toast.makeText(OTApplication.app, "Logged!", Toast.LENGTH_SHORT).show()
                OTBackgroundLoggingService.startLogging(OTApplication.app, trigger.tracker)
            }
            OTTrigger.ACTION_NOTIFICATION -> {
                println("trigger fired - send notification")
                OTNotificationManager.pushReminderNotification(OTApplication.app, trigger.tracker, triggerTime)
            }
        }


    }

    operator fun iterator(): MutableIterator<OTTrigger> {
        return triggers.iterator()
    }

    fun withIndex(): Iterable<IndexedValue<OTTrigger>> {
        return triggers.withIndex()
    }



}