package kr.ac.snu.hcil.omnitrack.ui.components.common.choice

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import kr.ac.snu.hcil.android.common.view.InterfaceHelper
import kr.ac.snu.hcil.omnitrack.R
import java.util.*
import kotlin.properties.Delegates

/**
 * Created by Young-Ho Kim on 2016-08-17.
 */
open class WordListView : FlexboxLayout {

    companion object {
        private var _colorPalette: IntArray? = null

        fun getColorPalette(context: Context): IntArray {
            if (_colorPalette == null) {
                _colorPalette = context.resources.getStringArray(R.array.choiceColorPaletteArray).map { Color.parseColor(it) }.toIntArray()
            }

            return _colorPalette!!
        }
    }

    var words: Array<String> by Delegates.observable(arrayOf()) {
        prop, old, new ->
        refresh()
    }

    var useColors = false

    val colorIndexList = ArrayList<Int>()

    var textAppearanceId: Int = R.style.tagTextAppearance
        set(value) {
            if (field != value) {
                field = value

                for (i in 0 until childCount) {
                    val c = getChildAt(i)
                    if (c is TextView) {
                        InterfaceHelper.setTextAppearance(c, textAppearanceId)
                    }
                }
            }
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attributeSet: AttributeSet) : super(context, attributeSet)
    constructor(context: Context, attributeSet: AttributeSet, defStyle: Int) : super(context, attributeSet, defStyle)

    init {
        flexDirection = FlexDirection.ROW
        flexWrap = FlexWrap.WRAP
    }

    fun refresh() {
        val numChildViewToAdd = words.size - childCount
        if (numChildViewToAdd < 0) {
            removeViews(childCount + numChildViewToAdd, -numChildViewToAdd)
        } else if (numChildViewToAdd > 0) {

            for (i in 0 until numChildViewToAdd) {
                addView(makeChildView(i))
            }
        }

        for (i in 0 until words.size) {
            val view = (getChildAt(i) as TextView)
            view.text = words[i]

            if (useColors) {
                val shape = (view.background as LayerDrawable).findDrawableByLayerId(R.id.layer_color_shape) as GradientDrawable
                shape.setColor(getColorPalette(context)[colorIndexList[i]])
            }

        }
    }

    protected open fun makeChildView(position: Int): TextView {
        val view = TextView(context)

        InterfaceHelper.setTextAppearance(view, textAppearanceId)

        view.setBackgroundResource(R.drawable.word_list_element_frame)

        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.leftMargin = 20
        view.layoutParams = lp

        return view
    }
}