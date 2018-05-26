package kr.ac.snu.hcil.omnitrack.ui.components.common

import android.annotation.TargetApi
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.support.constraint.ConstraintLayout
import android.support.constraint.ConstraintSet
import android.util.AttributeSet
import android.view.View
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.utils.events.Event

/**
 * Created by Young-Ho Kim on 2016-07-19
 */
class ColorPaletteView : ConstraintLayout, View.OnClickListener {
    private var selectedIndex: Int = 0
        set(value) {
            if (field != value) {
                field = value
                refreshUI()
                colorChanged.invoke(this, selectedColor)
            }
        }

    val colorChanged = Event<Int>()

    var colorPalette: IntArray = OTApp.instance.colorPalette
        set(newPalette) {
            if (!field.contentEquals(newPalette)) {
                field = newPalette
                refreshUI()
            }
        }

    var buttons = ArrayList<ColorSelectionButton>()

    init {
        refreshUI()
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var selectedColor: Int
        get() {
            return colorPalette[selectedIndex]
        }
        set(value) {
            val index = findColorIndex(value)
            if (index >= 0) {
                selectedIndex = index
            }
        }


    fun findColorIndex(color: Int): Int {
        return colorPalette.indexOf(color)
    }

    private fun refreshUI() {
        val lengthDiff = buttons.size - colorPalette.size
        if (lengthDiff > 0) {
            for (i in 1..lengthDiff)
                removeView(buttons[0])
            buttons.removeAt(0)
        } else if (lengthDiff < 0) {
            for (i in 1..-lengthDiff) {
                ColorSelectionButton(context).let {
                    it.layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
                    it.id = View.generateViewId()
                    it.setOnClickListener(this)
                    addView(it)
                    buttons.add(it)
                }
            }
        }

        if (colorPalette.size > 0) {
            val constraints = ConstraintSet()
            constraints.clone(this)

            buttons.forEachIndexed { index, button ->
                constraints.connect(button.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            }

            constraints.createHorizontalChain(ConstraintSet.PARENT_ID, ConstraintSet.LEFT, ConstraintSet.PARENT_ID, ConstraintSet.RIGHT, buttons.map { it.id }.toIntArray(), buttons.map { 1f }.toFloatArray(), ConstraintSet.CHAIN_SPREAD)

            constraints.applyTo(this)
        }

        colorPalette.forEachIndexed { index, color ->
            buttons[index].color = color
            buttons[index].isSelected = index == selectedIndex
        }
    }

    override fun onClick(v: View) {
        val index = buttons.indexOf(v as? ColorSelectionButton)
        if (index != -1) {
            selectedIndex = index
        }
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val thisState = SavedState(superState)
        thisState.selectedIndex = selectedIndex
        thisState.colorPalette = colorPalette
        return thisState
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        val thisState = state as SavedState
        super.onRestoreInstanceState(thisState.superState)
        selectedIndex = thisState.selectedIndex
        colorPalette = thisState.colorPalette ?: OTApp.instance.colorPalette
    }

    class SavedState : BaseSavedState {

        var selectedIndex: Int = 0
        var colorPalette: IntArray? = null

        constructor(source: Parcel) : super(source) {
            selectedIndex = source.readInt()
            colorPalette = source.createIntArray()
        }

        @TargetApi(24)
        constructor(source: Parcel, loader: ClassLoader?) : super(source, loader)
        constructor(superState: Parcelable?) : super(superState)


        override fun writeToParcel(out: Parcel, flags: Int) {
            super.writeToParcel(out, flags)
            out.writeInt(selectedIndex)
            out.writeIntArray(colorPalette)
        }

        companion object {
            @JvmField
            val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
                override fun createFromParcel(`in`: Parcel): SavedState {
                    return SavedState(`in`)
                }

                override fun newArray(size: Int): Array<SavedState?> {
                    return arrayOfNulls(size)
                }
            }
        }
    }
}