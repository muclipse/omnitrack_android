package kr.ac.snu.hcil.omnitrack.core.database.models

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Index
import io.realm.annotations.LinkingObjects
import io.realm.annotations.PrimaryKey
import kr.ac.snu.hcil.omnitrack.core.ItemLoggingSource
import kr.ac.snu.hcil.omnitrack.core.serialization.TypeStringSerializationHelper
import java.util.*

/**
 * Created by younghokim on 2017. 9. 25..
 */
open class OTItemDAO : RealmObject() {

    @Suppress("PropertyName")
    @PrimaryKey
    var _id: String? = null

    @Index
    var trackerId: String? = null

    var deviceId: String? = null

    @Index
    var timestamp: Long = System.currentTimeMillis()

    var timezone: String? = null

    var source: String? = null

    var fieldValueEntries = RealmList<OTItemValueEntryDAO>()

    var synchronizedAt: Long? = null // store server time of when synchronized perfectly.

    var userUpdatedAt: Long = System.currentTimeMillis()

    var serializedMetadata: String? = null

    @Index
    var removed: Boolean = false

    var loggingSource: ItemLoggingSource
        get() = if (source != null) {
            ItemLoggingSource.valueOf(source!!)
        } else ItemLoggingSource.Unspecified
        set(value) {
            source = value.name
        }

    fun serializedValueTable(): Map<String, String> {
        val table = Hashtable<String, String>()
        for (entryDAO in fieldValueEntries) {
            table[entryDAO.key] = entryDAO.value
        }
        return table
    }

    fun setValueOf(fieldLocalId: String, serializedValue: String?): Boolean {

        val match = fieldValueEntries.find { it.key == fieldLocalId }
        return if (match != null) {
            if (match.value != serializedValue) {
                match.value = serializedValue
                true
            } else false
        } else {
            fieldValueEntries.add(
                    OTItemValueEntryDAO().apply {
                        id = UUID.randomUUID().toString()
                        key = fieldLocalId
                        value = serializedValue
                    }
            )
        }
    }

    fun getValueOf(fieldLocalId: String): Any? {
        return fieldValueEntries.find { it.key == fieldLocalId }?.value?.let { TypeStringSerializationHelper.deserialize(it) }
    }
}

open class OTItemValueEntryDAO : RealmObject() {

    @PrimaryKey
    var id: String = ""

    @Index
    var key: String = ""

    var value: String? = null

    @LinkingObjects("fieldValueEntries")
    val items: RealmResults<OTItemDAO>? = null

    override fun toString(): String {
        return "{Item Value Entry | id : $id, key : $key, value : $value}"
    }
}