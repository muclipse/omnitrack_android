package kr.ac.snu.hcil.omnitrack.core.database

import android.support.annotation.Keep
import io.reactivex.Single
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.core.di.configured.FirebaseComponent

/**
 * Created by Young-Ho on 9/24/2017.
 */
@Keep
class OTDeviceInfo private constructor() {
    var os: String = OS
    var deviceId: String = OTApp.instance.deviceId
    var instanceId: String? = null
    var firstLoginAt: Long = System.currentTimeMillis()
    var appVersion: String = BuildConfig.VERSION_NAME

    companion object {

        val OS: String get() = "Android api-${android.os.Build.VERSION.SDK_INT}"

        fun makeDeviceInfo(firebaseComponent: FirebaseComponent): Single<OTDeviceInfo> {
            return firebaseComponent.getFirebaseInstanceIdToken().map { token ->
                OTDeviceInfo().apply {
                    instanceId = token
                }
            }
        }
    }
}