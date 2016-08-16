package kr.ac.snu.hcil.omnitrack.ui.components

import android.content.Context
import android.graphics.Canvas
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.widget.EditText

/**
 * Created by younghokim on 16. 7. 24..
 */
class LinedEditText : EditText {

    constructor(context: Context?) : super(context) {
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
    }

    private var base: LinedTextBase

    init {
        base = LinedTextBase(this)

        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                this@LinedEditText.setLineSpacing(0f, 1f)
                this@LinedEditText.setLineSpacing(base.lineSpacingExtra, base.lineSpacingMultiplier)
            }

        })
    }

    override fun onDraw(canvas: Canvas) {

        base.onDraw(canvas)

        super.onDraw(canvas);
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        base.onLayout(changed, left, top, right, bottom)
    }
}