package kr.ac.snu.hcil.omnitrack.core.attributes.properties

import android.content.Context
import com.google.gson.Gson
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.properties.APropertyView
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.properties.NumberStylePropertyView
import kr.ac.snu.hcil.omnitrack.utils.NumberStyle

/**
 * Created by Young-Ho Kim on 2016-09-05.
 */
class OTNumberStylePropertyHelper : OTPropertyHelper<NumberStyle>() {
    override fun getSerializedValue(value: NumberStyle): String {
        return Gson().toJson(value)
    }

    override fun parseValue(serialized: String): NumberStyle {
        return Gson().fromJson(serialized, NumberStyle::class.java)
    }

    override fun onBuildView(context: Context): APropertyView<NumberStyle> {
        return NumberStylePropertyView(context, null)
    }
}