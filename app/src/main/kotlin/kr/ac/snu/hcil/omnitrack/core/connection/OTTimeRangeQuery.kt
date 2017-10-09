package kr.ac.snu.hcil.omnitrack.core.connection

import android.content.Context
import android.text.format.DateUtils
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.OTItemBuilder
import kr.ac.snu.hcil.omnitrack.utils.serialization.ATypedQueueSerializable
import kr.ac.snu.hcil.omnitrack.utils.serialization.SerializableTypedQueue
import java.util.*

/**
 * Created by Young-Ho Kim on 2016-08-11
 */
class OTTimeRangeQuery : ATypedQueueSerializable {

    companion object {
        const val TYPE_PIVOT_TIMESTAMP = 0
        const val TYPE_PIVOT_KEY_TIME = 1
        const val TYPE_LINK_TIMESPAN = 2
        const val TYPE_PIVOT_TIMEPOINT = 3


        const val BIN_SIZE_HOUR = 0
        const val BIN_SIZE_DAY = 1
        const val BIN_SIZE_WEEK = 2
    }

    enum class Granularity {Millis, Second, Minute, Hour, Day, Week }

    enum class Preset(val nameResId: Int, val descResId: Int, val type: Int, val binSize: Int, val binOffset: Int, val anchorToNow: Boolean, val granularity: Granularity) {
        /*

    <string name="msg_present_date">Present date</string>
    <string name="msg_previous_date">Previous date</string>
    <string name="msg_recent_hour">Recent one hour</string>
    <string name="msg_recent_24_hours">Recent 24 hours</string>
    <string name="msg_recent_7_days">Recent 7 days</string>
    <string name="msg_present_week">Present week</string>
    <string name="msg_previous_week">Previous week</string>
         */
        PresentDate(R.string.msg_present_date, R.string.msg_present_date_desc, TYPE_PIVOT_TIMESTAMP, BIN_SIZE_DAY, 0, false, Granularity.Day),
        PreviousDate(R.string.msg_previous_date, R.string.msg_previous_date_desc, TYPE_PIVOT_TIMESTAMP, BIN_SIZE_DAY, -1, false, Granularity.Day),
        Recent1Hour(R.string.msg_recent_hour, R.string.msg_recent_hour_desc, TYPE_PIVOT_TIMESTAMP, BIN_SIZE_HOUR, 0, true, Granularity.Hour),
        Recent24Hours(R.string.msg_recent_24_hours, R.string.msg_recent_24_hours_desc, TYPE_PIVOT_TIMESTAMP, BIN_SIZE_DAY, 0, true, Granularity.Hour),
        Recent7Days(R.string.msg_recent_7_days, R.string.msg_recent_7_days_desc, TYPE_PIVOT_TIMESTAMP, BIN_SIZE_WEEK, 0, true, Granularity.Hour);

        fun makeQueryInstance(): OTTimeRangeQuery {
            return OTTimeRangeQuery(type, binSize, binOffset, anchorToNow)
        }

        fun equals(query: OTTimeRangeQuery): Boolean {
            return query.mode == type && query.binSize == binSize && query.binOffset == binOffset && query.anchorToNow == anchorToNow
        }
    }

    var mode: Int = TYPE_PIVOT_TIMESTAMP

    var anchorToNow: Boolean = false

    val isBinAndOffsetAvailable: Boolean
        get() = mode == TYPE_PIVOT_TIMEPOINT || mode == TYPE_PIVOT_TIMESTAMP || mode == TYPE_PIVOT_KEY_TIME

    val needsLinkedAttribute: Boolean
        get() = mode == TYPE_PIVOT_TIMEPOINT || mode == TYPE_LINK_TIMESPAN

    /*** not used in LINK_TIMESPAN mode.
     *
     */
    var binSize: Int = BIN_SIZE_DAY

    /*** not used in LINK_TIMESPAN mode.
     *
     */
    var binOffset: Int = 0

    var linkedAttributeId: String? = null

    constructor()

    constructor(mode: Int, binSize: Int, binOffset: Int = 0, anchorToNow: Boolean = false) {
        this.mode = mode
        this.binOffset = binOffset
        this.binSize = binSize
        this.anchorToNow = anchorToNow
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        else if (other is OTTimeRangeQuery) {
            return other.mode == this.mode &&
                    other.anchorToNow == this.anchorToNow &&
                    other.binOffset == this.binOffset &&
                    other.binSize == this.binSize &&
                    other.linkedAttributeId == this.linkedAttributeId
        } else return false
    }

    override fun onSerialize(typedQueue: SerializableTypedQueue) {
        typedQueue.putInt(mode)
        typedQueue.putBoolean(anchorToNow)
        if (isBinAndOffsetAvailable) {
            typedQueue.putInt(binSize)
            typedQueue.putInt(binOffset)
        }

        if (needsLinkedAttribute) {
            if (linkedAttributeId != null) {
                typedQueue.putString(linkedAttributeId!!)
            }
        }
    }

    override fun onDeserialize(typedQueue: SerializableTypedQueue) {
        mode = typedQueue.getInt()
        anchorToNow = typedQueue.getBoolean()
        if (isBinAndOffsetAvailable) {
            binSize = typedQueue.getInt()
            binOffset = typedQueue.getInt()
        }

        if (needsLinkedAttribute) {
            linkedAttributeId = typedQueue.getString()
        }
    }


    fun getRange(@Suppress("UNUSED_PARAMETER") builder: OTItemBuilder): Pair<Long, Long> {
        val start: Long
        val end: Long
        if (mode == TYPE_PIVOT_TIMESTAMP) {
            if (anchorToNow) {
                end = System.currentTimeMillis()
                start = end - when (binSize) {
                    BIN_SIZE_DAY -> DateUtils.DAY_IN_MILLIS
                    BIN_SIZE_HOUR -> DateUtils.HOUR_IN_MILLIS
                    BIN_SIZE_WEEK -> DateUtils.WEEK_IN_MILLIS
                    else -> 0L
                }
            } else {
                val cal = Calendar.getInstance()
                cal.set(Calendar.MILLISECOND, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MINUTE, 0)
                if (binSize == BIN_SIZE_DAY) {
                    cal.set(Calendar.HOUR_OF_DAY, 0)
                }

                when (binSize) {
                    BIN_SIZE_DAY -> cal.add(Calendar.DAY_OF_YEAR, binOffset)
                    BIN_SIZE_HOUR -> cal.add(Calendar.HOUR_OF_DAY, binOffset)
                    BIN_SIZE_WEEK -> cal.add(Calendar.WEEK_OF_YEAR, binOffset)
                }
                start = cal.timeInMillis

                when (binSize) {
                    BIN_SIZE_DAY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                    BIN_SIZE_HOUR -> cal.add(Calendar.HOUR_OF_DAY, 1)
                    BIN_SIZE_WEEK -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                }

                end = cal.timeInMillis
            }

            println("Range to be queried: $start ~ $end")
            return Pair(start, end)
        } else {
            //TODO implement other types
            throw NotImplementedError()
        }
    }

    fun getModeName(context: Context): CharSequence {
        if (mode == TYPE_PIVOT_TIMESTAMP) {
            return context.resources.getString(R.string.msg_connection_wizard_time_query_pivot_present)
        } else if (mode == TYPE_PIVOT_TIMEPOINT || mode == TYPE_LINK_TIMESPAN) {
            val fieldNameFormat = context.resources.getString(R.string.msg_connection_wizard_time_query_pivot_field_format)
            return String.format(fieldNameFormat, "Other")
        } else return "None"
    }

    fun getScopeName(context: Context): CharSequence {
        return when (binSize) {
            BIN_SIZE_DAY -> context.resources.getString(R.string.msg_connection_wizard_time_query_scope_day)
            BIN_SIZE_HOUR -> context.resources.getString(R.string.msg_connection_wizard_time_query_scope_hour)
            BIN_SIZE_WEEK -> context.resources.getString(R.string.msg_connection_wizard_time_query_scope_week)
            else -> "None"
        }
    }

}