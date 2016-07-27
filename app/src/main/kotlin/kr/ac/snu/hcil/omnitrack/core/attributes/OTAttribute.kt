package kr.ac.snu.hcil.omnitrack.core.attributes

import android.content.Context
import android.util.SparseArray
import android.view.View
import com.google.gson.Gson
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.OTUser
import kr.ac.snu.hcil.omnitrack.core.NamedObject
import kr.ac.snu.hcil.omnitrack.core.attributes.OTNumberAttribute
import kr.ac.snu.hcil.omnitrack.core.attributes.OTTimeAttribute
import kr.ac.snu.hcil.omnitrack.core.attributes.properties.OTProperty
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes.AAttributeInputView
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes.NumberInputView
import kr.ac.snu.hcil.omnitrack.utils.ReadOnlyPair
import kr.ac.snu.hcil.omnitrack.utils.events.Event
import kr.ac.snu.hcil.omnitrack.utils.serialization.SerializedIntegerKeyEntry
import java.util.*
import kotlin.properties.Delegates

/**
 * Created by Young-Ho on 7/11/2016.
 */
abstract class OTAttribute<DataType>(objectId: String?, dbId: Long?, columnName: String, val typeId: Int, settingData: String?) : NamedObject(objectId, dbId, columnName) {
    override fun makeNewObjectId(): String {
        return owner?.owner?.makeNewObjectId() ?: UUID.randomUUID().toString()
    }

    companion object {

        const val TYPE_NUMBER = 0
        const val TYPE_TIME = 1
        const val TYPE_TIMESPAN = 2
        const val TYPE_SHORT_TEXT = 3
        const val TYPE_LONG_TEXT = 4
        const val TYPE_LOCATION = 5


        fun createAttribute(objectId: String?, dbId: Long?, columnName: String, typeId: Int, settingData: String?): OTAttribute<out Any> {
            val attr = when (typeId) {
                TYPE_NUMBER -> OTNumberAttribute(objectId, dbId, columnName, settingData)
                TYPE_TIME -> OTTimeAttribute(objectId, dbId, columnName, settingData)
                TYPE_LONG_TEXT -> OTLongTextAttribute(objectId, dbId, columnName, settingData)
                else -> OTNumberAttribute(objectId, dbId, columnName, settingData)
            }
            return attr
        }

        fun createAttribute(user: OTUser, columnName: String, typeId: Int): OTAttribute<out Any> {
            return createAttribute(user.getNewAttributeObjectId().toString(), null, columnName, typeId, null)
        }


    }

    val removedFromTracker = Event<OTTracker>()
    val addedToTracker = Event<OTTracker>()

    abstract val keys: Array<Int>

    abstract val typeNameResourceId: Int
        get

    val propertyValueChanged = Event<OTProperty.PropertyChangedEventArgs<out Any>>()
    private val settingsProperties = SparseArray<OTProperty<out Any>>()

    constructor(columnName: String, typeId: Int) : this(null, null, columnName, typeId, null)

    init {
        createProperties()
        if (settingData != null) {

            val parser = Gson()
            val s = parser.fromJson(settingData, Array<String>::class.java).map { parser.fromJson(it, SerializedIntegerKeyEntry::class.java) }

            for (entry in s) {
                setPropertyValueFromSerializedString(entry.key, entry.value)
            }
        }
    }

    protected abstract fun createProperties()

    fun getSerializedProperties(): String {
        val s = ArrayList<String>()
        val parser = Gson()
        for (key in keys) {
            s.add(parser.toJson(SerializedIntegerKeyEntry(key, getProperty<Any>(key).getSerializedValue())))
        }

        return parser.toJson(s.toTypedArray())
    }

    abstract val typeNameForSerialization: String

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
            onPropertyValueChanged(args)
        }

        settingsProperties.put(property.key, property)
    }

    protected open fun onPropertyValueChanged(args: OTProperty.PropertyChangedEventArgs<out Any>) {
        propertyValueChanged.invoke(this, args)
    }

    fun <T> getProperty(key: Int): OTProperty<T> {
        @Suppress("UNCHECKED_CAST")
        return settingsProperties[key]!! as OTProperty<T>
    }

    fun <T> getPropertyValue(key: Int): T {
        return getProperty<T>(key).value
    }

    fun setPropertyValue(key: Int, value: Any) {
        getProperty<Any>(key).value = value
    }

    fun setPropertyValueFromSerializedString(key: Int, serializedValue: String) {
        getProperty<Any>(key).setValueFromSerializedString(serializedValue)
    }

    abstract fun formatAttributeValue(value: Any): String

    abstract fun makeDefaultValue(): DataType

    abstract fun getInputViewType(previewMode: Boolean = false): Int

    open fun makePropertyViews(context: Context): Collection<ReadOnlyPair<Int?, View>> {
        val result = ArrayList<ReadOnlyPair<Int?, View>>()
        for (key in keys) {
            result.add(ReadOnlyPair(key, getProperty<Any>(key).buildView(context)))
        }
        return result
    }

    //reuse recycled view if possible.
    open fun getInputView(context: Context, previewMode: Boolean, recycledView: AAttributeInputView<out Any>?): AAttributeInputView<out Any> {
        val view =
                if ((recycledView?.typeId == getInputViewType(previewMode))) {
                    recycledView!!
                } else {
                    AAttributeInputView.makeInstance(getInputViewType(previewMode), context)
                }

        refreshInputViewContents(view)
        view.previewMode = previewMode
        return view
    }

    abstract fun refreshInputViewContents(inputView: AAttributeInputView<out Any>);


}