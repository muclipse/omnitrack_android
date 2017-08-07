package kr.ac.snu.hcil.omnitrack.core.visualization.models

import android.util.SparseIntArray
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.attributes.OTChoiceAttribute
import kr.ac.snu.hcil.omnitrack.core.database.DatabaseManager
import kr.ac.snu.hcil.omnitrack.core.visualization.AttributeChartModel
import kr.ac.snu.hcil.omnitrack.core.visualization.interfaces.ICategoricalBarChart
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.AChartDrawer
import kr.ac.snu.hcil.omnitrack.ui.components.visualization.drawers.CategoricalBarChartDrawer
import rx.Observable
import java.util.*

/**
 * Created by younghokim on 16. 9. 7..
 */
class ChoiceCategoricalBarChartModel(override val attribute: OTChoiceAttribute) : AttributeChartModel<ICategoricalBarChart.Point>(attribute), ICategoricalBarChart {

    private val counterDictCache = SparseIntArray() // entry id : count
    private val categoriesCache = HashSet<Int>()

    override val name: String
        get() = String.format(OTApplication.app.resourcesWrapped.getString(R.string.msg_vis_categorical_distribution_title_format), super.name)

    override fun recycle() {
        counterDictCache.clear()
        categoriesCache.clear()
    }

    override fun reloadData(): Observable<List<ICategoricalBarChart.Point>> {
        val tracker = attribute.tracker
        if (tracker != null) {
            return DatabaseManager.loadItems(tracker, getTimeScope()).map {
                items ->

                val data = ArrayList<ICategoricalBarChart.Point>()

                counterDictCache.clear()
                categoriesCache.clear()

                var noResponseCount = 0

                println("items during ${getTimeScope()}: ${items.size}")
                items.forEach {
                    println(it.timestampString)
                }

                items.map { it.getValueOf(attribute) as? IntArray }
                        .forEach {
                            if (it != null && it.isNotEmpty()) {
                                for (id in it) {
                                    if (categoriesCache.contains(id) == false) {
                                        categoriesCache.add(id)
                                        counterDictCache.put(id, 1)
                                    } else {
                                        counterDictCache.put(id, counterDictCache[id] + 1)
                                    }
                                }
                            } else {
                                noResponseCount++
                            }
                        }

                synchronized(data) {
                    data.clear()
                    for (categoryId in categoriesCache) {
                        val entry = attribute.entries.findWithId(categoryId)
                        println("entry: ${entry?.text}, count: ${counterDictCache[categoryId]}")
                        if (entry != null) {
                            println("entry add")
                            data.add(ICategoricalBarChart.Point(entry.text, counterDictCache[categoryId].toDouble(), categoryId))
                        }
                    }
                }

                println("result data: " + data)

                categoriesCache.clear()
                counterDictCache.clear()

                return@map data
            }
        } else {
            throw IllegalArgumentException("No tracker is assigned in the field.")
        }
    }

    override fun getChartDrawer(): AChartDrawer {
        val drawer = CategoricalBarChartDrawer()
        drawer.integerValues = true

        return drawer
    }

}