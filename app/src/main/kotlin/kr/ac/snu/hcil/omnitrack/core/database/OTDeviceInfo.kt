package kr.ac.snu.hcil.omnitrack.core.database

import android.support.annotation.Keep
import com.google.firebase.iid.FirebaseInstanceId
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.OTApp

/**
 * Created by Young-Ho on 9/24/2017.
 */
@Keep
class OTDeviceInfo {
    var os: String = "Android api-${android.os.Build.VERSION.SDK_INT}"
    var deviceId: String = OTApp.instance.deviceId
    var instanceId: String? = FirebaseInstanceId.getInstance().token
    var firstLoginAt: Long = System.currentTimeMillis()
    var appVersion: String = BuildConfig.VERSION_NAME
}