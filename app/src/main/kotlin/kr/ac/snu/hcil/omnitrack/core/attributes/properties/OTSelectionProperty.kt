package kr.ac.snu.hcil.omnitrack.core.attributes.properties

import android.content.Context
import kr.ac.snu.hcil.omnitrack.core.OTProperty
import kr.ac.snu.hcil.omnitrack.ui.components.properties.APropertyView
import kr.ac.snu.hcil.omnitrack.ui.components.properties.SelectionPropertyView

/**
 * Created by younghokim on 16. 7. 12..
 */
class OTSelectionProperty(key: Int, title: String, private val values: List<String>) : OTProperty<Int>(key, title) {
    override fun buildView(context: Context): APropertyView<Int> {
        val result = SelectionPropertyView(context, null)
        result.setValues(values)
        result.title = title
        return result
    }

}