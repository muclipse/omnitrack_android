package kr.ac.snu.hcil.omnitrack.ui.components.visualization.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import kr.ac.snu.hcil.android.common.dipSize
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.IDrawer
import kotlin.properties.Delegates

/**
 * Created by Young-Ho on 9/8/2016.
 */
class Axis(val context: Context, var pivot: Pivot) : IDrawer {

    enum class Pivot {
        LEFT, BOTTOM
    }

    enum class TickLabelStyle {

        Small {
            override fun applyStyle(axis: Axis) {

                axis.labelPaint.textSize = axis.context.resources.getDimension(R.dimen.vis_axis_label_numeric_size)
                axis.labelPaint.isFakeBoldText = true
                axis.labelSpacing = dipSize(axis.context, 2)
            }

        },
        Normal {
            override fun applyStyle(axis: Axis) {
                axis.labelPaint.textSize = axis.context.resources.getDimension(R.dimen.vis_axis_label_categorical_size)
                axis.labelPaint.isFakeBoldText = false
                axis.labelSpacing = axis.context.resources.getDimension(R.dimen.vis_axis_label_spacing)
            }
        };


        abstract fun applyStyle(axis: Axis)
    }

    var drawBar: Boolean = true
    var drawGridLines: Boolean = false

    var linePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    var gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var labelSpacing: Float = 0f

    var gridOnBorder: Boolean = false

    var attachedTo: RectF = RectF()
        set(value) {
            if (field != value) {
                field = value
                when (pivot) {
                    Pivot.BOTTOM -> scale?.setRealCoordRange(value.left, value.right)
                    Pivot.LEFT -> scale?.setRealCoordRange(value.bottom, value.top)
                }
            }
        }

    var scale: IAxisScale<*>? = null

    private val tickLabelSizeMeasureRect = Rect()

    var style: TickLabelStyle by Delegates.observable(TickLabelStyle.Normal) {
        prop, old, new ->
        new.applyStyle(this)
    }

    init {
        linePaint.color = ContextCompat.getColor(context, R.color.vis_color_axis)
        linePaint.style = Paint.Style.STROKE
        linePaint.strokeWidth = context.resources.getDimension(R.dimen.vis_axis_thickness)

        gridLinePaint.color = ColorUtils.setAlphaComponent(linePaint.color, 50)
        gridLinePaint.style = Paint.Style.STROKE
        gridLinePaint.strokeWidth = context.resources.getDimension(R.dimen.vis_axis_grid_thickness)

        labelPaint.style = Paint.Style.FILL
        labelPaint.color = ContextCompat.getColor(context, R.color.textColorMid)
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.textSize = context.resources.getDimension(R.dimen.vis_axis_label_categorical_size)

        labelSpacing = context.resources.getDimension(R.dimen.vis_axis_label_spacing)
    }

    override fun onDraw(canvas: Canvas) {

        when (pivot) {
            Pivot.BOTTOM -> labelPaint.textAlign = Paint.Align.CENTER
            Pivot.LEFT -> labelPaint.textAlign = Paint.Align.RIGHT
        }

        if (drawBar) {
            when (pivot) {
                Pivot.BOTTOM -> canvas.drawLine(attachedTo.left, attachedTo.bottom, attachedTo.right, attachedTo.bottom, linePaint)

                Pivot.LEFT -> canvas.drawLine(attachedTo.left, attachedTo.bottom, attachedTo.left, attachedTo.top, linePaint)
            }
        }

        for (i in 0 until (scale?.numTicks ?: 0)) {
            val tickCoord = scale?.getTickCoordAt(i) ?: 0f

            val tickLabel = scale?.getTickLabelAt(i)
            if (!tickLabel.isNullOrBlank()) {
                when (pivot) {
                    Pivot.BOTTOM ->
                        canvas.drawText(tickLabel, tickCoord, attachedTo.bottom + labelSpacing + labelPaint.textSize, labelPaint)
                    Pivot.LEFT -> {
                        labelPaint.getTextBounds(tickLabel, 0, tickLabel.length, tickLabelSizeMeasureRect)
                        canvas.drawText(tickLabel, attachedTo.left - labelSpacing, tickCoord + tickLabelSizeMeasureRect.height() / 2, labelPaint)
                    }
                }
            }

            if (drawGridLines) {

                val gridCoord = if (gridOnBorder && i < scale?.numTicks ?: 0 - 1) {
                    tickCoord + (scale?.getTickInterval() ?: 0f) / 2
                } else {
                    tickCoord
                }

                when (pivot) {
                    Pivot.BOTTOM -> {

                        canvas.drawLine(gridCoord, attachedTo.top, gridCoord, attachedTo.bottom, gridLinePaint)
                    }
                    Pivot.LEFT ->
                        canvas.drawLine(attachedTo.left, gridCoord, attachedTo.right, gridCoord, gridLinePaint)
                }
            }
        }

    }

}