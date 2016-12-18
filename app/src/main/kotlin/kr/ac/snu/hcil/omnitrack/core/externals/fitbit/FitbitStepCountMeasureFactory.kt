package kr.ac.snu.hcil.omnitrack.core.externals.fitbit

import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.connection.OTTimeRangeQuery
import kr.ac.snu.hcil.omnitrack.core.externals.OTExternalService
import kr.ac.snu.hcil.omnitrack.core.externals.OTMeasureFactory
import kr.ac.snu.hcil.omnitrack.utils.auth.OAuth2Client
import kr.ac.snu.hcil.omnitrack.utils.serialization.SerializableTypedQueue
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper
import org.json.JSONObject
import java.util.*

/**
 * Created by younghokim on 16. 9. 3..
 */
object FitbitStepCountMeasureFactory : OTMeasureFactory() {

    override fun getExampleAttributeConfigurator(): IExampleAttributeConfigurator {
        return CONFIGURATOR_STEP_ATTRIBUTE
    }

    override val supportedConditionerTypes: IntArray = CONDITIONERS_FOR_SINGLE_NUMERIC_VALUE

    override val exampleAttributeType: Int = OTAttribute.TYPE_NUMBER

    override fun isAttachableTo(attribute: OTAttribute<out Any>): Boolean {
        return attribute.typeId == OTAttribute.TYPE_NUMBER
    }

    override val isRangedQueryAvailable: Boolean = true
    override val isDemandingUserInput: Boolean = false

    override fun makeMeasure(): OTMeasure {
        return FitbitStepMeasure()
    }

    override fun makeMeasure(serialized: String): OTMeasure {
        return FitbitStepMeasure(serialized)
    }

    override val service: OTExternalService = FitbitService

    override val descResourceId: Int = R.string.measure_fitbit_steps_desc
    override val nameResourceId: Int = R.string.measure_fitbit_steps_name


    class FitbitStepMeasure : OTRangeQueriedMeasure {

        override val dataTypeName: String = TypeStringSerializationHelper.TYPENAME_INT

        override val factory: OTMeasureFactory = FitbitStepCountMeasureFactory

        val converter = object : OAuth2Client.OAuth2RequestConverter<Int?> {
            override fun process(requestResultStrings: Array<String>): Int? {
                val json = JSONObject(requestResultStrings.first())
                println("convert $json")
                if (json.has("summary")) {
                    val steps = json.getJSONObject("summary").getInt("steps")
                    return steps

                } else return null
            }

        }

        constructor() : super()
        constructor(serialized: String) : super(serialized)


        override fun awaitRequestValue(query: OTTimeRangeQuery?): Any {
            throw UnsupportedOperationException("not implemented") //To change body of created functions use File | Settings | File Templates.
        }

        override fun requestValueAsync(start: Long, end: Long, handler: (Any?) -> Unit) {

            OTApplication.logger.writeSystemLog("Start getting Fitbit Step Log", "FitbitStepFactory")
            FitbitService.request(
                    FitbitService.makeRequestUrlWithCommandAndDate(FitbitService.REQUEST_COMMAND_SUMMARY, Date(start)),
                    converter)
            {
                result ->
                OTApplication.logger.writeSystemLog("Finished getting Fitbit Step Log", "FitbitStepFactory")
                println("Fitbit step result: $result")
                handler.invoke(result)
            }
        }

        override fun onDeserialize(typedQueue: SerializableTypedQueue) {
        }

        override fun onSerialize(typedQueue: SerializableTypedQueue) {
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            else if (other is FitbitStepMeasure) {
                return true
            } else return false
        }

        override fun hashCode(): Int {
            return factoryCode.hashCode()
        }
    }
}