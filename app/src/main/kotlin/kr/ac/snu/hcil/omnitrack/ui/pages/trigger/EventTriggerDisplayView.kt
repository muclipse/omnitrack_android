package kr.ac.snu.hcil.omnitrack.ui.pages.trigger

import android.content.Context
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.calculation.SingleNumericComparison
import kr.ac.snu.hcil.omnitrack.core.externals.OTMeasureFactory
import kr.ac.snu.hcil.omnitrack.utils.setPaddingTop
import java.text.DecimalFormat

/**
 * Created by Young-Ho on 9/5/2016.
 */
class EventTriggerDisplayView: LinearLayout {

    private val measureNameView: TextView
    private val symbolView: AppCompatImageView
    private val comparedNumberView: TextView

    val numberFormat = DecimalFormat("#,###.###")

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)

    init{
        orientation = VERTICAL

        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        inflater.inflate(R.layout.trigger_display_event, this, true)

        measureNameView = findViewById(R.id.ui_measure_name) as TextView
        symbolView = findViewById(R.id.ui_comparison_symbol) as AppCompatImageView
        comparedNumberView = findViewById(R.id.ui_compared_number) as TextView
    }

    fun setMeasureFactory(factory: OTMeasureFactory?)
    {
        measureNameView.text = factory?.getFormattedName()
    }

    fun setConditioner(conditioner: SingleNumericComparison?)
    {
        if(conditioner==null)
        {
            symbolView.visibility = GONE
            measureNameView.visibility = GONE


            comparedNumberView.setText(R.string.msg_trigger_event_msg_tap_to_configure)

            comparedNumberView.setPaddingTop((10f * resources.displayMetrics.density + .5f).toInt())
            comparedNumberView.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20f * resources.displayMetrics.density)
        }
        else{
            symbolView.visibility = View.VISIBLE
            measureNameView.visibility = VISIBLE

            symbolView.setImageResource(conditioner.method.symbolImageResourceId)

            comparedNumberView.setPaddingTop(0)
            comparedNumberView.text = numberFormat.format(conditioner.comparedTo)
            comparedNumberView.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.number_digit_size))

        }

    }
}