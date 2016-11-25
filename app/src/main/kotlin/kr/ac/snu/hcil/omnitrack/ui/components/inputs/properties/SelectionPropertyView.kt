package kr.ac.snu.hcil.omnitrack.ui.components.inputs.properties

import android.content.Context
import android.util.AttributeSet
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.ui.components.common.choice.SelectionView

/**
 * Created by Young-Ho Kim on 2016-07-20.
 */
class SelectionPropertyView(context: Context, attrs: AttributeSet?) : APropertyView<Int>(R.layout.component_property_selection, context, attrs) {

    private var selectionView: SelectionView

    override var value: Int
        get() = selectionView.selectedIndex
        set(value) {
            selectionView.selectedIndex = value
        }

    override fun focus() {

    }

    init {
        selectionView = findViewById(R.id.value) as SelectionView
        selectionView.onSelectedIndexChanged += {
            sender, index ->
            onValueChanged(index)
        }
    }

    fun setEntries(values: Array<String>) {
        selectionView.setValues(values)
    }

}