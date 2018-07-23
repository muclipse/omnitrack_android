package kr.ac.snu.hcil.omnitrack.core.triggers.conditions

import com.google.gson.JsonObject

/**
 * Created by younghokim on 2017. 10. 18..
 */
abstract class ATriggerCondition(val type: Byte) : Cloneable {
    open val isSticky: Boolean = false
    abstract fun getSerializedString(): String

    abstract fun isConfigurationValid(validationErrorMessages: MutableList<CharSequence>?): Boolean

    abstract fun writeEventLogContent(table: JsonObject)

    abstract fun makeInformationText(): CharSequence

    public override fun clone(): Any {
        return super.clone()
    }
}