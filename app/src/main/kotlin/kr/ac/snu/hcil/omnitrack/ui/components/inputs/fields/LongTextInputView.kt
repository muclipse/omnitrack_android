package kr.ac.snu.hcil.omnitrack.ui.components.inputs.fields

import android.content.Context
import android.util.AttributeSet
import kr.ac.snu.hcil.omnitrack.R

/**
 * Created by younghokim on 16. 7. 24..
 */
class LongTextInputView(context: Context, attrs: AttributeSet? = null) : ACharSequenceFieldInputView(R.layout.input_longtext, context, attrs) {

    override val typeId: Int = VIEW_TYPE_LONG_TEXT
}