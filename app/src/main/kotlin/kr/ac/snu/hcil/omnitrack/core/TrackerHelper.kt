package kr.ac.snu.hcil.omnitrack.core

import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttributeManager
import kr.ac.snu.hcil.omnitrack.core.attributes.helpers.ISingleNumberAttributeHelper
import kr.ac.snu.hcil.omnitrack.core.database.local.models.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.visualization.ChartModel
import kr.ac.snu.hcil.omnitrack.core.visualization.models.DailyCountChartModel
import kr.ac.snu.hcil.omnitrack.core.visualization.models.DurationHeatMapModel
import kr.ac.snu.hcil.omnitrack.core.visualization.models.LoggingHeatMapModel
import kr.ac.snu.hcil.omnitrack.core.visualization.models.TimeSeriesPlotModel
import java.util.*

/**
 * Created by younghokim on 2017. 10. 16..
 */
object TrackerHelper {

    fun makeRecommendedChartModels(trackerDao: OTTrackerDAO, realm: Realm): Array<ChartModel<*>> {

        val list = ArrayList<ChartModel<*>>()

        val timePointAttributes = trackerDao.attributes.filter { !it.isHidden && !it.isInTrashcan && it.type == OTAttributeManager.TYPE_TIME }

        val timeSpanAttributes = trackerDao.attributes.filter { !it.isHidden && !it.isInTrashcan && it.type == OTAttributeManager.TYPE_TIMESPAN }

        val singleNumberAttributes = trackerDao.attributes.filter { !it.isHidden && !it.isInTrashcan && it.getHelper() is ISingleNumberAttributeHelper }

        //generate tracker-level charts

        list += DailyCountChartModel(trackerDao, realm, timePointAttributes.firstOrNull())
        list += LoggingHeatMapModel(trackerDao, realm, timePointAttributes.firstOrNull())

        //durationTimeline
        for (timeSpanAttr in timeSpanAttributes) {
            for (numAttr in singleNumberAttributes) {
                list.add(DurationHeatMapModel(trackerDao, timeSpanAttr, numAttr, realm))
            }
        }

        //add line timeline if numeric variables exist
        /*
        val numberAttrs = trackerDao.makeAttributesQuery(false,false).equalTo("type", OTAttributeManager.TYPE_NUMBER).findAll()
        if (numberAttrs.isNotEmpty()) {
            list.add(TimelineComparisonLineChartModel(numberAttrs, trackerDao, realm))
        }*/

        //add time-value scatterplots
        for (timeAttr in timePointAttributes) {
            for (numAttr in singleNumberAttributes) {
                list.add(TimeSeriesPlotModel(trackerDao, timeAttr, numAttr, realm))
            }
        }

        for (attribute in trackerDao.attributes.filter { !it.isHidden && !it.isInTrashcan }) {
            list.addAll(attribute.getHelper().makeRecommendedChartModels(attribute, realm))
        }

        return list.toTypedArray()
    }
}