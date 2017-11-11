package kr.ac.snu.hcil.omnitrack.core.externals.rescuetime

import com.google.gson.stream.JsonReader
import io.reactivex.Flowable
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttributeManager
import kr.ac.snu.hcil.omnitrack.core.connection.OTTimeRangeQuery
import kr.ac.snu.hcil.omnitrack.core.database.local.models.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.core.externals.OTExternalService
import kr.ac.snu.hcil.omnitrack.core.externals.OTMeasureFactory
import kr.ac.snu.hcil.omnitrack.utils.Nullable
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper
import org.json.JSONObject
import java.util.*

/**
 * Created by Young-Ho Kim on 2016-09-02.
 */
object RescueTimeProductivityMeasureFactory : OTMeasureFactory("prd") {


    val configurator = object : IExampleAttributeConfigurator {
        override fun configureExampleAttribute(attr: OTAttribute<out Any>): Boolean {
            /*if (attr is OTNumberAttribute) {
                val ns = NumberStyle()
                ns.fractionPart = 0
                ns.commaUnit = 0
                return true
            } else*/ return false
        }
    }

    override fun getExampleAttributeConfigurator(): IExampleAttributeConfigurator {
        return configurator
    }

    override val exampleAttributeType: Int = OTAttributeManager.TYPE_NUMBER

    override fun getService(): OTExternalService {
        return RescueTimeService
    }

    override fun isAttachableTo(attribute: OTAttributeDAO): Boolean {
        return attribute.type == OTAttributeManager.TYPE_NUMBER
    }

    override val isRangedQueryAvailable: Boolean = true

    override val minimumGranularity: OTTimeRangeQuery.Granularity = OTTimeRangeQuery.Granularity.Hour

    override val isDemandingUserInput: Boolean = false

    override fun makeMeasure(): OTMeasure {
        return ProductivityMeasure()
    }

    override fun makeMeasure(reader: JsonReader): OTMeasure {
        return ProductivityMeasure()
    }

    override fun makeMeasure(serialized: String): OTMeasure {
        return ProductivityMeasure()
    }

    override fun serializeMeasure(measure: OTMeasure): String {
        return "{}"
    }

    override val nameResourceId: Int = R.string.measure_rescuetime_productivity_name
    override val descResourceId: Int = R.string.measure_rescuetime_productivity_desc

    val productivityCalculator = object : RescueTimeService.ISummaryCalculator<Double> {
        override fun calculate(list: List<JSONObject>, startDate: Date, endDate: Date): Double? {
            return if (list.size > 0) {
                list.map { it.getDouble(RescueTimeService.SUMMARY_VARIABLE_PRODUCTIVITY) }.sum() / list.size
            } else null
        }

    }

    class ProductivityMeasure : OTRangeQueriedMeasure() {

        override val dataTypeName: String = TypeStringSerializationHelper.TYPENAME_DOUBLE
        override val factory: OTMeasureFactory = RescueTimeProductivityMeasureFactory

        override fun getValueRequest(start: Long, end: Long): Flowable<Nullable<out Any>> {
            return RescueTimeService.getSummaryRequest(Date(start), Date(end - 1), productivityCalculator).toFlowable() as Flowable<Nullable<out Any>>
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            else return other is ProductivityMeasure
        }

        override fun hashCode(): Int {
            return factoryCode.hashCode()
        }

    }

}