package kr.ac.snu.hcil.omnitrack.core.attributes.helpers

import android.Manifest
import android.content.Context
import android.location.Location
import android.view.View
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.maps.model.LatLng
import com.patloew.rxlocation.RxLocation
import io.reactivex.BackpressureStrategy
import io.reactivex.Single
import io.realm.Realm
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.attributes.FallbackPolicyResolver
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttributeManager
import kr.ac.snu.hcil.omnitrack.core.database.local.models.OTAttributeDAO
import kr.ac.snu.hcil.omnitrack.statistics.NumericCharacteristics
import kr.ac.snu.hcil.omnitrack.ui.components.common.LiteMapView
import kr.ac.snu.hcil.omnitrack.ui.components.inputs.attributes.AAttributeInputView
import kr.ac.snu.hcil.omnitrack.utils.Nullable
import kr.ac.snu.hcil.omnitrack.utils.serialization.TypeStringSerializationHelper
import java.util.concurrent.TimeUnit

/**
 * Created by Young-Ho on 10/7/2017.
 */
class OTLocationAttributeHelper : OTAttributeHelper() {

    private val permissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)

    override val supportedFallbackPolicies: LinkedHashMap<Int, FallbackPolicyResolver>
        get() = super.supportedFallbackPolicies.apply{
            this[OTAttributeDAO.DEFAULT_VALUE_POLICY_FILL_WITH_INTRINSIC_VALUE] = object: FallbackPolicyResolver(R.string.msg_intrinsic_location, isValueVolatile = true)
            {
                override fun getFallbackValue(attribute: OTAttributeDAO, realm: Realm): Single<Nullable<out Any>> {
                    return Single.defer {
                        val rxLocation = RxLocation(OTApp.instance)
                        val request = LocationRequest.create() //standard GMS LocationRequest
                                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                                .setNumUpdates(2)
                                .setInterval(100)
                                .setMaxWaitTime(500)
                                .setExpirationDuration(2000)

                        try {
                            return@defer rxLocation.location().updates(request, BackpressureStrategy.LATEST)
                                    .map { location -> LatLng(location.latitude, location.longitude) }
                                    .timeout(2L, TimeUnit.SECONDS, rxLocation.location().lastLocation().map { location -> LatLng(location.latitude, location.longitude) }.toFlowable())
                                    .firstOrError()
                                    .map { loc->Nullable(loc) }
                        }catch(ex: SecurityException)
                        {
                            ex.printStackTrace()
                            return@defer Single.just(Nullable(LatLng(0.0,0.0)))
                        }
                    }
                }

            }

            this.remove(OTAttributeDAO.DEFAULT_VALUE_POLICY_NULL)
        }

    override fun getValueNumericCharacteristics(attribute: OTAttributeDAO): NumericCharacteristics = NumericCharacteristics(false, true)

    override fun getTypeNameResourceId(attribute: OTAttributeDAO): Int = R.string.type_location_name

    override fun getTypeSmallIconResourceId(attribute: OTAttributeDAO): Int = R.drawable.icon_small_location

    override fun getRequiredPermissions(attribute: OTAttributeDAO?): Array<String>? = permissions

    override val typeNameForSerialization: String = TypeStringSerializationHelper.TYPENAME_LATITUDE_LONGITUDE

    override fun getInputViewType(previewMode: Boolean, attribute: OTAttributeDAO): Int = AAttributeInputView.VIEW_TYPE_LOCATION

    override fun formatAttributeValue(attribute: OTAttributeDAO, value: Any): CharSequence {
        return if (value is LatLng) {
            "${Location.convert(value.latitude, Location.FORMAT_DEGREES)},${Location.convert(value.longitude, Location.FORMAT_DEGREES)}"
        } else super.formatAttributeValue(attribute, value)
    }

    override fun initialize(attribute: OTAttributeDAO) {
        attribute.fallbackValuePolicy = OTAttributeDAO.DEFAULT_VALUE_POLICY_FILL_WITH_INTRINSIC_VALUE
    }

    //item list===========================================================================
    override fun getViewForItemListContainerType(): Int {
        return OTAttributeManager.VIEW_FOR_ITEM_LIST_CONTAINER_TYPE_MULTILINE
    }

    override fun getViewForItemList(attribute: OTAttributeDAO, context: Context, recycledView: View?): View {
        return recycledView as? LiteMapView ?: LiteMapView(context)
    }

    override fun applyValueToViewForItemList(attribute: OTAttributeDAO, value: Any?, view: View): Single<Boolean> {
        return Single.defer {
            if (view is LiteMapView && value != null) {
                if (value is LatLng) {
                    view.location = value
                    Single.just(true)
                } else Single.just(false)
            } else super.applyValueToViewForItemList(attribute, value, view)
        }
    }
}