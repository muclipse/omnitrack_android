package kr.ac.snu.hcil.omnitrack.core.attributes.properties

import java.util.*

/**
 * Created by Young-Ho on 10/8/2017.
 */
object OTPropertyManager {
    enum class EPropertyType {
        Boolean, ChoiceEntryList, NumberStyle, RatingOptions, Selection, Number
    }

    private val propertyHelperTable = Hashtable<EPropertyType, OTPropertyHelper<out Any>>()

    fun getHelper(type: EPropertyType): OTPropertyHelper<out Any> {
        val helperInTable = propertyHelperTable[type]
        return if (helperInTable == null) {
            val newHelper = when (type) {
                EPropertyType.Boolean -> OTBooleanPropertyHelper()
                EPropertyType.ChoiceEntryList -> OTChoiceEntryListPropertyHelper()
                EPropertyType.NumberStyle -> OTNumberStylePropertyHelper()
                EPropertyType.RatingOptions -> OTRatingOptionsPropertyHelper()
                EPropertyType.Selection -> OTSelectionPropertyHelper()
                EPropertyType.Number -> OTNumberPropertyHelper()
            }
            propertyHelperTable.set(type, newHelper)

            newHelper
        } else helperInTable
    }
}