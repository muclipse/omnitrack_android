package kr.ac.snu.hcil.omnitrack.core

import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.utils.serialization.IStringSerializable

/**
 * Created by younghokim on 16. 7. 22..
 */
abstract class ADataRow() : IStringSerializable {
    abstract fun getValueOf(attribute: OTAttribute<out Any>): Any?
    abstract fun <T> getCastedValueOf(attribute: OTAttribute<T>): T?
    abstract fun hasValueOf(attribute: OTAttribute<out Any>): Boolean
    abstract fun getNumColumns(): Int

    abstract fun extractFormattedStringArray(scheme: OTTracker): Array<String?>

    abstract fun extractValueArray(scheme: OTTracker): Array<Any?>
}