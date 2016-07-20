package kr.ac.snu.hcil.omnitrack.core

import android.util.SparseArray
import com.google.gson.Gson
import kr.ac.snu.hcil.omnitrack.core.attributes.OTNumberAttribute
import kr.ac.snu.hcil.omnitrack.core.attributes.properties.OTProperty
import kr.ac.snu.hcil.omnitrack.utils.events.Event
import java.util.*
import kotlin.properties.Delegates

/**
 * Created by Young-Ho on 7/11/2016.
 */
open abstract class OTAttribute<DataType>(objectId: String?, dbId: Long?, columnName: String, val typeName: String, settingData: String?) : UniqueObject(objectId, dbId, columnName) {

    companion object {

        const val TYPE_NUMBER = "Number"
        const val TYPE_TIME = "Time"
        const val TYPE_TIMESPAN = "Timespan"
        const val TYPE_LOCATION = "Location"


        fun CreateAttribute(objectId: String?, dbId: Long?, columnName: String, typeName: String, settingData: String): OTAttribute<out Any> {
            val attr = when (typeName) {
                TYPE_NUMBER -> OTNumberAttribute(objectId, dbId, columnName, settingData)
                else -> OTNumberAttribute(objectId, dbId, columnName, settingData)
            }



            return attr
        }
    }

    val removedFromTracker = Event<OTTracker>()
    val addedToTracker = Event<OTTracker>()

    abstract val keys: Array<Int>

    val onPropertyChanged = Event<OTProperty.PropertyChangedEventArgs<out Any>>()
    private val settingsProperties = SparseArray<OTProperty<out Any>>()

    constructor(columnName: String, typeName: String) : this(null, null, columnName, typeName, null)

    init {
        createProperties()
        if (settingData != null) {
            //TODO assign values
        }
    }

    protected abstract fun createProperties()

    fun getSerializedProperties(): String {
        return ""
    }

    var owner: OTTracker? by Delegates.observable(null as OTTracker?) {
        prop, old, new ->
        if (old != null) {
            removedFromTracker.invoke(this, old)
        }
        if (new != null) {
            addedToTracker.invoke(this, new)
        }
    }

    protected fun assignProperty(property: OTProperty<out Any>) {
        property.onValueChanged += {
            sender, args ->
            onPropertyChanged.invoke(this, args)
        }

        settingsProperties.put(property.key, property)
    }

    protected fun <T> getProperty(key: Int): OTProperty<T> {
        return settingsProperties[key]!! as OTProperty<T>
    }

    protected fun <T> getPropertyValue(key: Int): T {
        return getProperty<T>(key).value
    }

    protected fun setPropertyValue(key: Int, value: Any) {
        getProperty<Any>(key).value = value
    }


}