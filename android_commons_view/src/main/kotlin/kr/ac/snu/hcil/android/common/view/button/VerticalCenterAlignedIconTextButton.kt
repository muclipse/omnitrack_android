package kr.ac.snu.hcil.android.common.view.button

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import kr.ac.snu.hcil.android.common.view.tint.TintFancyButton

/**
 * Created by younghokim on 2017. 5. 24..
 */
class VerticalCenterAlignedIconTextButton : TintFancyButton {
    constructor(context: Context?) : super(context) {
        align()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        align()
    }

    private fun align() {
        if (iconImageObject != null) {
            val newParams = LinearLayout.LayoutParams(iconImageObject.layoutParams)
            newParams.gravity = Gravity.CENTER_VERTICAL
            iconImageObject.layoutParams = newParams
        }
    }
}