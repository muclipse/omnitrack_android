package kr.ac.snu.hcil.omnitrack.core.analytics

import com.google.gson.JsonObject
import kr.ac.snu.hcil.omnitrack.core.ItemLoggingSource

/**
 * Created by younghokim on 2017. 11. 28..
 */
interface IEventLogger {

    companion object {

        const val NAME_AUTH = "auth"

        const val NAME_CHANGE_ATTRIBUTE = "change_attribute"
        const val NAME_CHANGE_TRACKER = "change_tracker"
        const val NAME_CHANGE_TRIGGER = "change_trigger"
        const val NAME_CHANGE_ITEM = "change_item"

        const val NAME_CHANGE_SERVICE = "change_service"

        const val NAME_DEVICE_EVENT = "device"

        const val NAME_TRACKER_REORDER = "reorder_trackers"

        const val SUB_ADD = "add"
        const val SUB_REMOVE = "remove"
        const val SUB_EDIT = "edit"

        const val SUB_SIGNED_IN = "signedIn"
        const val SUB_SIGNED_OUT = "signedOut"
        const val SUB_CONSENT_DENIED = "consentDenied"
        const val SUB_CONSENT_APPROVED = "consentApproved"

        const val CONTENT_IS_INDIVIDUAL = "isIndividual"
        const val CONTENT_KEY_PROPERTY = "property"
        const val CONTENT_KEY_NEWVALUE = "newValue"

        const val NAME_TRACKER_DATA_EXPORT = "tracker_export_data"

        const val NAME_SESSION = "session"
        const val SUB_SESSION_TYPE_ACTIVITY = "activity"
        const val SUB_SESSION_TYPE_FRAGMENT = "fragment"

        const val SUB_ITEM_REMOVED_FROM_LIST = "list"
        const val SUB_ITEM_REMOVED_FROM_NOTI = "noti"
    }

    fun logEvent(name: String, sub: String?, content: JsonObject? = null, timestamp: Long = System.currentTimeMillis())

    fun logAttributeChangeEvent(sub: String?, attributeLocalId: String, trackerId: String?, inject: ((JsonObject) -> Unit)? = null)
    fun logTrackerChangeEvent(sub: String?, trackerId: String?, inject: ((JsonObject) -> Unit)? = null)
    fun logTrackerOnShortcutChangeEvent(trackerId: String?, isOnShortcut: Boolean)
    fun logTriggerChangeEvent(sub: String?, triggerId: String?, inject: ((JsonObject) -> Unit)? = null)
    fun logSession(sessionName: String, sessionType: String, elapsed: Long, finishedAt: Long, from: String?, inject: ((JsonObject) -> Unit)? = null)
    fun logExport(trackerId: String?)

    fun logItemEditEvent(itemId: String, inject: ((JsonObject) -> Unit)? = null)
    fun logItemAddedEvent(itemId: String, source: ItemLoggingSource, inject: ((JsonObject) -> Unit)? = null)
    fun logItemRemovedEvent(itemId: String, removedFrom: String, inject: ((JsonObject) -> Unit)? = null)

    fun logTrackerReorderEvent()

    fun logServiceActivationChangeEvent(serviceCode: String, isActivated: Boolean, inject: ((JsonObject) -> Unit)? = null)
}