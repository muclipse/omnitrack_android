package kr.ac.snu.hcil.omnitrack.core.visualization.models

import android.content.Context
import android.graphics.Canvas
import android.text.format.DateUtils
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import io.reactivex.Single
import io.realm.Realm
import io.realm.Sort
import kr.ac.snu.hcil.android.common.time.getHourOfDay
import kr.ac.snu.hcil.android.common.time.setHourOfDay
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.database.models.OTFieldDAO
import kr.ac.snu.hcil.omnitrack.core.database.models.OTItemDAO
import kr.ac.snu.hcil.omnitrack.core.database.models.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.types.TimePoint
import kr.ac.snu.hcil.omnitrack.core.visualization.INativeChartModel
import kr.ac.snu.hcil.omnitrack.core.visualization.TrackerChartModel
import kr.ac.snu.hcil.omnitrack.core.visualization.interfaces.ITimeBinnedHeatMap
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.AChartDrawer
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.components.Axis
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.components.IAxisScale
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.components.element.DataEncodedDrawingList
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.components.element.RectElement
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.components.scales.CategoricalAxisScale
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.components.scales.QuantizedTimeScale
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.drawers.ATimelineChartDrawer
import java.util.*

/**
 * Created by Young-Ho Kim on 9/9/2016
 */


class LoggingHeatMapModel(tracker: OTTrackerDAO, realm: Realm, val context: Context, timeField: OTFieldDAO? = null) : TrackerChartModel<ITimeBinnedHeatMap.CounterVector>(tracker, realm), ITimeBinnedHeatMap, INativeChartModel {

    val hoursInYBin = 2

    val timeAttributeLocalId: String? = timeField?.localId
    val timeAttributeName: String? = timeField?.name

    init {
        (context.applicationContext as OTAndroidApp).applicationComponent.inject(this)
    }

    private fun getTimestampOfItem(item: OTItemDAO): Long? {
        return if (timeAttributeLocalId == null) {
            item.timestamp
        } else (item.getValueOf(timeAttributeLocalId) as? TimePoint)?.timestamp
    }

    override fun reloadData(): Single<List<ITimeBinnedHeatMap.CounterVector>> {
        val data = ArrayList<ITimeBinnedHeatMap.CounterVector>()
        val xScale = QuantizedTimeScale(context)
        xScale.setDomain(getTimeScope().from, getTimeScope().to)
        xScale.quantize(currentGranularity)

        /*
        Y scale is 2-hour-length bin, ranged from 0 to 24, bins are 12
         */

        //make 2D array
        val countMatrix = Array(xScale.numTicks) { index ->
            IntArray(24 / hoursInYBin)
        }

        val calendarCache = Calendar.getInstance()



        return if (timeAttributeLocalId != null) {
            Single.just(dbManager.getItemsQueriedWithTimeAttribute(tracker._id, getTimeScope(),
                    timeAttributeLocalId, realm).sortedBy { (it.getValueOf(timeAttributeLocalId) as TimePoint).timestamp })
        } else {
            dbManager
                    .makeItemsQuery(tracker._id, getTimeScope(), realm)
                    .sort("timestamp", Sort.ASCENDING)
                    .findAllAsync()
                    .asFlowable()
                    .filter { it.isLoaded }
                    .firstOrError()
        }.map { items ->
            //println(items)

            var currentItemPointer = 0

            for (xIndex in 0 until xScale.numTicks) {
                //from this tick to next tick
                val from = xScale.binPointsOnDomain[xIndex]

                val to = if (xIndex < xScale.numTicks - 1) xScale.binPointsOnDomain[xIndex + 1]
                else getTimeScope().to

                var currentTime = from
                var binIndex: Int
                while (currentTime < to) {
                    binIndex = 0
                    while (binIndex * hoursInYBin < 24) {
                        calendarCache.timeInMillis = currentTime + hoursInYBin * binIndex * DateUtils.HOUR_IN_MILLIS

                        val hourOfDay = calendarCache.getHourOfDay()
                        val queryFrom = calendarCache.timeInMillis
                        val queryTo = queryFrom + hoursInYBin * DateUtils.HOUR_IN_MILLIS

                        var counter = 0
                        //counter = items.filter{ it.timestamp >= queryFrom && it.timestamp < queryTo }.size

                        if (currentItemPointer < items.size) {
                            var timestamp = getTimestampOfItem(items[currentItemPointer]) ?: items[currentItemPointer].timestamp

                            while (timestamp < queryTo) {
                                if (timestamp >= queryFrom) {
                                    counter++
                                }

                                currentItemPointer++
                                if (currentItemPointer >= items.size) break
                                timestamp = getTimestampOfItem(items[currentItemPointer]) ?: items[currentItemPointer].timestamp

                            }
                        }

                        //println("rows during ${TimeHelper.FORMAT_DATETIME.format(Date(queryFrom))} ~ ${TimeHelper.FORMAT_DATETIME.format(Date(queryTo))} : count: ${counter}")

                        countMatrix[xIndex][hourOfDay / hoursInYBin] += counter

                        binIndex++
                    }

                    currentTime += DateUtils.DAY_IN_MILLIS
                }


            }

            var maxValue = Int.MIN_VALUE
            for (x in countMatrix) {
                for (y in x) {
                    maxValue = Math.max(y, maxValue)
                }
            }

            synchronized(data)
            {
                data.clear()
                countMatrix.withIndex().mapTo(data) { xEntry -> ITimeBinnedHeatMap.CounterVector(xScale.binPointsOnDomain[xEntry.index], xEntry.value.map { it / maxValue.toFloat() }.toFloatArray()) }
            }

            data.toList()
        }
    }

    override val name: String
        get() {
            return if (timeAttributeLocalId != null) {
                String.format(context.getString(R.string.msg_vis_logging_heatmap_title_format_with_time_attr), tracker.name, timeAttributeName)
            } else String.format(context.resources.getString(R.string.msg_vis_logging_heatmap_title_format), tracker.name)
        }

    override fun getChartDrawer(): AChartDrawer {
        return HeatMapDrawer()
    }

    inner class HeatMapDrawer : ATimelineChartDrawer(context) {
        override val aspectRatio: Float = 1.7f

        val verticalAxis = Axis(context, Axis.Pivot.LEFT)

        val yScale = CategoricalAxisScale()

        var cellColumns = DataEncodedDrawingList<ITimeBinnedHeatMap.CounterVector, Void?>()

        init {

            horizontalAxis.gridLinePaint.color = ContextCompat.getColor(context, R.color.frontalBackground)
            verticalAxis.gridLinePaint.color = ContextCompat.getColor(context, R.color.frontalBackground)

            verticalAxis.style = Axis.TickLabelStyle.Small

            horizontalAxis.gridOnBorder = true
            verticalAxis.gridOnBorder = true

            yScale.tickFormat = object : IAxisScale.ITickFormat<Int> {
                override fun format(value: Int, index: Int): String {
                    val hourOfDay = index * hoursInYBin
                    if (hourOfDay % 6 == 0) {
                        return String.format("%02d", hourOfDay) + ":00"
                    } else return ""
                }

            }


            horizontalAxis.drawGridLines = true
            horizontalAxis.drawBar = false

            verticalAxis.drawGridLines = true
            verticalAxis.drawBar = false

            yScale.setCategories(*Array(24 / hoursInYBin) { index ->
                val cal = Calendar.getInstance()
                cal.setHourOfDay(index * hoursInYBin)
                (index * hoursInYBin).toString()
            }).inverse()

            verticalAxis.scale = yScale
            children.add(verticalAxis)

            children.add(cellColumns)
        }


        override fun onResized() {
            super.onResized()
            verticalAxis.attachedTo = plotAreaRect

            cellColumns.onResizedCanvas { datum, column ->
                if (column is DataEncodedDrawingList<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    val columnGroup = column as DataEncodedDrawingList<Float, ITimeBinnedHeatMap.CounterVector>
                    columnGroup.onResizedCanvas { count, cell ->
                        if (cell is RectElement<Float>) {
                            mapCellRectToSpace(cell, datum.value.time, count.index)
                        }
                    }
                }
            }

        }

        override fun onModelChanged() {

        }

        private fun mapCellRectToSpace(cell: RectElement<Float>, x: Long, y: Int) {
            val centerX = xScale[x]
            val centerY = yScale[y]
            val width = Math.abs(xScale.getTickInterval()) - 4
            val height = Math.abs(yScale.getTickInterval()) - 4

            cell.bound.set(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2)
        }

        override fun onRefresh() {
            super.onRefresh()

            if (model is LoggingHeatMapModel) {
                cellColumns.setData(this@LoggingHeatMapModel.cachedData)
                cellColumns.appendEnterSelection { datum ->

                    val cellGroup = DataEncodedDrawingList<Float, ITimeBinnedHeatMap.CounterVector>()
                    cellGroup.setData(
                            datum.value.distribution.toList()
                    )

                    cellGroup.appendEnterSelection { count ->
                        val newCell = RectElement<Float>()
                        newCell.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.colorPointed), (255 * count.value + 0.5f).toInt())
                        mapCellRectToSpace(newCell, datum.value.time, count.index)
                        newCell
                    }
                    cellGroup
                }

                cellColumns.updateElement { datum, column ->
                    @Suppress("UNCHECKED_CAST")
                    val rectList = column as DataEncodedDrawingList<Float, ITimeBinnedHeatMap.CounterVector>
                    rectList.setData(datum.value.distribution.toList())
                    rectList.appendEnterSelection { count ->
                        val newCell = RectElement<Float>()
                        newCell.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.colorPointed), (255 * count.value + 0.5f).toInt())
                        mapCellRectToSpace(newCell, datum.value.time, count.index)
                        newCell
                    }

                    rectList.updateElement { count, cell ->
                        mapCellRectToSpace(cell as RectElement<Float>, datum.value.time, count.index)
                        cell.color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.colorPointed), (255 * count.value + 0.5f).toInt())
                    }

                    rectList.removeElements(rectList.getExitElements())

                }

                cellColumns.removeElements(cellColumns.getExitElements())
            }
        }

        override fun onDraw(canvas: Canvas) {
            fillRect(plotAreaRect, ContextCompat.getColor(context, R.color.editTextFormBackground), canvas)

            super.onDraw(canvas)
        }

    }
}