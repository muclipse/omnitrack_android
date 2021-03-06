package kr.ac.snu.hcil.omnitrack.core.externals.fitbit

import android.content.Context
import com.google.gson.JsonObject
import io.reactivex.Single
import kr.ac.snu.hcil.android.common.containers.Nullable
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.connection.OTTimeRangeQuery
import kr.ac.snu.hcil.omnitrack.core.externals.OTServiceMeasureFactory
import kr.ac.snu.hcil.omnitrack.core.fields.OTFieldManager
import kr.ac.snu.hcil.omnitrack.core.serialization.TypeStringSerializationHelper
import org.json.JSONObject

/**
 * Created by Young-Ho Kim on 2016-10-06.
 */
class FitbitHeartRateMeasureFactory(context: Context, service: FitbitService) : OTServiceMeasureFactory(context, service, "heart") {

    override val dataTypeName: String = TypeStringSerializationHelper.TYPENAME_INT

    override fun getAttributeType() = OTFieldManager.TYPE_NUMBER

    override val isRangedQueryAvailable: Boolean = true
    override val minimumGranularity: OTTimeRangeQuery.Granularity? = OTTimeRangeQuery.Granularity.Hour
    override val isDemandingUserInput: Boolean = false

    override fun makeMeasure(arguments: JsonObject?): OTMeasure {
        return FitbitHeartRateMeasure(this, arguments)
    }

    override val nameResourceId: Int = R.string.measure_fitbit_heart_rate_name
    override val descResourceId: Int = R.string.measure_fitbit_heart_rate_desc

    class FitbitHeartRateMeasure(factory: FitbitHeartRateMeasureFactory, arguments: JsonObject?) : OTRangeQueriedMeasure(factory, arguments) {
        companion object {
            val converter = object : FitbitApi.AIntraDayConverter<Int, Int>("activities-heart-intraday") {
                override fun extractValueFromDatum(datum: JSONObject): Int {
                    return datum.getInt("value")
                }

                override fun processValues(values: List<Int>): Int {
                    return (values.average() + 0.5).toInt()
                }
            }
        }

        override fun getValueRequest(start: Long, end: Long): Single<Nullable<out Any>> {
            val urls = FitbitApi.makeIntraDayRequestUrls(FitbitApi.REQUEST_INTRADAY_RESOURCE_PATH_HEART_RATE, start, end)
            println(urls)
            return getFactory<FitbitHeartRateMeasureFactory>().getService<FitbitService>().getRequest(converter, *urls) as Single<Nullable<out Any>>
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            else return other is FitbitHeartRateMeasure
        }

        override fun hashCode(): Int {
            return factoryCode.hashCode()
        }

    }
}