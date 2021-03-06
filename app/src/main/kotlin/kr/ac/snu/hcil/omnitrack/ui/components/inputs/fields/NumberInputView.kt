package kr.ac.snu.hcil.omnitrack.ui.components.inputs.fields

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.text.style.TextAppearanceSpan
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import kr.ac.snu.hcil.android.common.isNumericPrimitive
import kr.ac.snu.hcil.android.common.toBigDecimal
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.types.NumberStyle
import java.math.BigDecimal
import kotlin.properties.Delegates

/**
 * Created by Young-Ho Kim on 2016-07-22.
 */
class NumberInputView(context: Context, attrs: AttributeSet? = null, initialValue: BigDecimal? = null) : AFieldInputView<BigDecimal>(R.layout.input_number, context, attrs) {

    override val typeId: Int = VIEW_TYPE_NUMBER
    override var value: BigDecimal? = initialValue
        set(value) {
            if (field != value) {
                field = value
                applyValueToView()
                onValueChanged(value)
            }
        }

    var moveUnit: BigDecimal = BigDecimal(1)
        set(value) {
            if (field != value) {
                field = value
            }
        }

    private val increaseButton: View = findViewById(R.id.ui_button_plus)
    private val decreaseButton: View = findViewById(R.id.ui_button_minus)
    private val valueField: EditText = findViewById(R.id.ui_value_field)
    private val valueStatic: TextView = findViewById(R.id.ui_value_static)

    private val formattedInformation = NumberStyle.FormattedInformation(null, "", NumberStyle.UnitPosition.None)

    var numberStyle: NumberStyle = NumberStyle()
        set(value) {
            if (field != value) {
                field = value
                applyValueToView()
            }
        }

    var typingMode: Boolean by Delegates.observable(false)
    {
        prop, old, new ->
        if (old != new) {
            if (new) {
                applyValueToView()
                valueStatic.visibility = GONE
                valueField.visibility = VISIBLE
                valueField.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(valueField, InputMethodManager.SHOW_IMPLICIT)
            } else {

                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(valueField.windowToken, 0)
                valueStatic.visibility = VISIBLE
                valueField.visibility = GONE
            }

        }
    }


    private val onButtonClickedHandler = {
        view: View ->
        if (typingMode) {
            applyFieldValue()
            typingMode = false
        }
        when (view.id) {
            R.id.ui_button_plus -> {
                value = (value ?: BigDecimal(0)) + moveUnit
            }
            R.id.ui_button_minus ->
                value = (value ?: BigDecimal(0)) - moveUnit
        }
    }

    init {

        valueField.visibility = GONE

        valueStatic.setOnClickListener {
            view ->
            typingMode = true
        }

        valueField.setOnFocusChangeListener { view, hasFocus ->
            if (!hasFocus) {
                applyFieldValue()
                typingMode = false
            }
        }

        valueField.setOnEditorActionListener { textView, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_DONE || keyEvent?.action == KeyEvent.ACTION_DOWN && keyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                applyFieldValue()
                typingMode = false
                true
            } else false
        }

        increaseButton.setOnClickListener(onButtonClickedHandler)
        decreaseButton.setOnClickListener(onButtonClickedHandler)

        applyValueToView()
    }

    fun applyValueToView() {

        val value = this.value
        if (value == null) {
            valueStatic.text = null
            valueField.setText(null, TextView.BufferType.NORMAL)
            valueField.setSelection(valueField.text.length)
        } else {
            val formattedText = numberStyle.formatNumber(value, formattedInformation)

            val spanned = SpannableString(formattedText)
            if (numberStyle.unitPosition != NumberStyle.UnitPosition.None) {
                spanned.setSpan(TextAppearanceSpan(context, R.style.numberInputViewUnitStyle), formattedInformation.unitPartStart, formattedInformation.unitPartEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            spanned.setSpan(StyleSpan(Typeface.ITALIC), formattedInformation.numberPartStart, formattedInformation.numberPartEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            valueStatic.text = spanned
            valueField.setText(value.toPlainString(), TextView.BufferType.NORMAL)
            valueField.setSelection(valueField.text.length)
        }
    }

    private fun applyFieldValue() {
        try {
            value = if (valueField.text.isNotBlank()) {
                BigDecimal(valueField.text.toString())
            } else {
                null
            }
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }

    override fun focus() {

    }

    override fun setAnyValue(value: Any?) {
        println(value)
        if (value == null) {
            this.value = null
        } else if (isNumericPrimitive(value)) {
                this.value = toBigDecimal(value)
        } else {
            super.setAnyValue(value)
        }
    }

    override fun clearFocus() {
        valueField.clearFocus()
    }

}