package kr.ac.snu.hcil.omnitrack.core.externals

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.support.v4.app.Fragment
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.subjects.BehaviorSubject
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.core.dependency.OTSystemDependencyResolver
import kr.ac.snu.hcil.omnitrack.core.dependency.PermissionDependencyResolver
import kr.ac.snu.hcil.omnitrack.ui.pages.services.ExternalServiceActivationActivity
import kr.ac.snu.hcil.omnitrack.utils.INameDescriptionResourceProvider
import rx_activity_result2.RxActivityResult
import java.util.*
import javax.inject.Inject
import javax.inject.Provider

/**
 * Created by younghokim on 16. 7. 28..
 */
abstract class OTExternalService(val context: Context, val identifier: String, val minimumSDK: Int) : INameDescriptionResourceProvider {
    /*
    companion object {

        private val factoryCodeDict = HashMap<String, OTMeasureFactory>()

        val availableServices: Array<OTExternalService> by lazy {
            arrayOf(
                    //AndroidDeviceService,
                    GoogleFitService
                    , FitbitService
                    , JawboneUpService
                    , MisfitService
                    , RescueTimeService
                    //,MicrosoftBandService
                    //,MiBandService
            ).filter { service -> service.isSupportedInSystem() }.toTypedArray()
        }

        fun findServiceByIdentifier(identifier: String): OTExternalService? {
            return availableServices.find { it.identifier == identifier }
        }

        fun getFilteredMeasureFactories(filter: (OTMeasureFactory) -> Boolean): List<OTMeasureFactory> {

            val list = ArrayList<OTMeasureFactory>()
            for (service in OTExternalService.availableServices) {
                for (factory in service.measureFactories) {
                    if (filter(factory)) {
                        list.add(factory)
                    }
                }
            }

            return list
        }

        fun getMeasureFactoryByCode(typeCode: String): OTMeasureFactory? {
            return factoryCodeDict[typeCode]
        }

        val preferences: SharedPreferences by lazy { OTApp.instance.getSharedPreferences("ExternalServices", Context.MODE_PRIVATE) }

        fun init() {
            for (service in availableServices) {
                service.initialize()
                for (factory in service.measureFactories) {
                    println("service: ${service.identifier}")
                    println("factory service: ${factory.getService()}")
                    factoryCodeDict.put(factory.typeCode, factory)
                }
            }
        }


        /***
         * Get whether the service's activation state stored in system
         */
        fun getIsActivatedFlag(service: OTExternalService): Boolean {
            return getIsActivatedFlag(service.identifier)
        }

        fun getIsActivatedFlag(serviceIdentifier: String): Boolean {
            return preferences.getBoolean(serviceIdentifier + "_activated", false)
        }

        fun setIsActivatedFlag(service: OTExternalService, isActivated: Boolean) {
            preferences.edit().putBoolean(service.identifier + "_activated", isActivated).apply()
        }
    }*/

    enum class ServiceState {
        DEACTIVATED, ACTIVATING, ACTIVATED
    }


    @Inject
    protected lateinit var externalServiceManager: Provider<OTExternalServiceManager>

    open val isInternetRequiredForActivation = true

    private val _dependencyList = ArrayList<OTSystemDependencyResolver>()
    val dependencyList: List<OTSystemDependencyResolver> get() = _dependencyList

    open val permissionGranted: Boolean
        get() {
            for (permission in getRequiredPermissionsRecursive()) {
                if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return false
                }
            }
            return true
        }

    private val _measureFactories = ArrayList<OTMeasureFactory>()

    val measureFactories: List<OTMeasureFactory> get() {
        return _measureFactories
    }

    var state: ServiceState
        get() = onStateChanged.value ?: ServiceState.DEACTIVATED
        protected set(value) {
            if (onStateChanged.value != value) {
                onStateChanged.onNext(value)
            }
        }

    val onStateChanged: BehaviorSubject<ServiceState> = BehaviorSubject.create<ServiceState>().apply { onNext(ServiceState.DEACTIVATED) }

    init {

        (context.applicationContext as OTAndroidApp).applicationComponent.inject(this)

        state = if (externalServiceManager.get().getIsActivatedFlag(identifier) == true) {
            ServiceState.ACTIVATED
        } else {
            ServiceState.DEACTIVATED
        }
    }

    internal fun initialize() {
        _measureFactories += onRegisterMeasureFactories()

        val permissions = getRequiredPermissionsRecursive()
        if (permissions.isNotEmpty()) {
            _dependencyList.add(
                    PermissionDependencyResolver(*permissions)
            )
        }

        _dependencyList += onRegisterDependencies()
    }

    internal abstract fun isSupportedInSystem(): Boolean

    protected abstract fun onRegisterMeasureFactories(): Array<OTMeasureFactory>

    protected open fun onRegisterDependencies(): Array<OTSystemDependencyResolver> {
        return emptyArray()
    }

    fun activateSilently(): Single<Boolean> {
        return prepareServiceAsync()
                .map {
                    resultState ->
                    resultState >= OTSystemDependencyResolver.DependencyState.NonFatalFailed
                }
                .doOnSuccess {
                    result ->
                    if (result == true) {
                        externalServiceManager.get().setIsActivatedFlag(this, true)
                        state = ServiceState.ACTIVATED
                    } else {
                        externalServiceManager.get().setIsActivatedFlag(this, false)
                        state = ServiceState.DEACTIVATED
                    }
                }
    }

    fun startActivationActivityAsync(context: Context): Observable<Boolean> {

        return RxActivityResult.on(context as Activity)
                .startIntent(ExternalServiceActivationActivity.makeIntent(context, this))
                .map {
                    result ->
                    result.resultCode() == Activity.RESULT_OK
                }
    }


    fun deactivate(): Completable {
        return Completable.defer {
            externalServiceManager.get().setIsActivatedFlag(this, false)
            state = ServiceState.DEACTIVATED
            onDeactivate()
        }

    }

    abstract fun onDeactivate(): Completable

    abstract val thumbResourceId: Int

    fun grantPermissions(caller: Fragment, requestCode: Int) {
        caller.requestPermissions(getRequiredPermissionsRecursive(), requestCode)
    }

    open fun prepareServiceAsync(): Single<OTSystemDependencyResolver.DependencyState> {

        return Single.zip(dependencyList.map { it.checkDependencySatisfied(context, false) },
                { results ->
                    OTSystemDependencyResolver.combineDependencyState(*(results.map {
                        (it as OTSystemDependencyResolver.DependencyCheckResult).state
                    }.toTypedArray()))
                })

    }

    protected fun getRequiredPermissionsRecursive(): Array<String> {
        val result = HashSet<String>()

        for (factory in _measureFactories) {
            result.addAll(factory.requiredPermissions)
        }

        return result.toTypedArray()
    }
}