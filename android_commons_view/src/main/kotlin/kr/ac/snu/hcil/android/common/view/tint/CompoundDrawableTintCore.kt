package kr.ac.snu.hcil.android.common.view.tint

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.AttributeSet
import androidx.appcompat.content.res.AppCompatResources
import kr.ac.snu.hcil.android.common.dipSize
import kr.ac.snu.hcil.android.common.view.R
import kr.ac.snu.hcil.android.common.view.applyTint
import kotlin.math.roundToInt

/**
 * Created by younghokim on 2017. 1. 13..
 */
class CompoundDrawableTintCore {


    private val LEFT = 0
    private val TOP = 1
    private val RIGHT = 2
    private val BOTTOM = 3

    fun init(context: Context, compoundDrawables: Array<Drawable?>, attrs: AttributeSet, defStyleAttr: Int): Array<Drawable?> {

        /*
        val isRequired = android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M
        if (!isRequired) {
            return
        }*/

        val a = context.obtainStyledAttributes(attrs, R.styleable.DrawableTint,
                defStyleAttr, 0)

        try {

            val drawableStart = getDrawableCompat(R.styleable.DrawableTint_tintDrawableStartCompat, a, context)
            if (drawableStart != null) {
                compoundDrawables[LEFT] = drawableStart
            }

            val drawableEnd = getDrawableCompat(R.styleable.DrawableTint_tintDrawableEndCompat, a, context)
            if (drawableEnd != null) {
                compoundDrawables[RIGHT] = drawableEnd
            }

            val hasColor = a.hasValue(R.styleable.DrawableTint_drawableTint)
            if (hasColor) {
                val color = a.getColor(R.styleable.DrawableTint_drawableTint,
                        Color.TRANSPARENT)
                compoundDrawables[LEFT] = tint(compoundDrawables[LEFT], color)
                compoundDrawables[TOP] = tint(compoundDrawables[TOP], color)
                compoundDrawables[RIGHT] = tint(compoundDrawables[RIGHT], color)
                compoundDrawables[BOTTOM] = tint(compoundDrawables[BOTTOM], color)
            }

            for (drawable in compoundDrawables) {
                if (drawable?.bounds?.isEmpty == true) {
                    drawable.setBounds(0, 0, dipSize(context, 24).roundToInt(), dipSize(context, 24).roundToInt())
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            a.recycle()
        }

        return compoundDrawables
    }

    private fun getDrawableCompat(attrId: Int, a: TypedArray, context: Context): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                a.getDrawable(attrId)
            } else {
                val resourceId = a.getResourceId(attrId, -1)
                AppCompatResources.getDrawable(context, resourceId)
            }
        } catch(ex: Exception) {
            null
        }

    }

    private fun tint(drawable: Drawable?, color: Int): Drawable? {
        drawable?.let {
            return applyTint(drawable, color)
        }
        return null
    }
}