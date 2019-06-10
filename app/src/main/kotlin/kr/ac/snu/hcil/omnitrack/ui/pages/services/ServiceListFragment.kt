package kr.ac.snu.hcil.omnitrack.ui.pages.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.github.pwittchen.reactivenetwork.library.rx2.ReactiveNetwork
import io.reactivex.Completable
import kotlinx.android.synthetic.main.fragment_home_services.*
import kotlinx.android.synthetic.main.fragment_home_services.view.*
import kr.ac.snu.hcil.android.common.dipSize
import kr.ac.snu.hcil.android.common.view.DialogHelper
import kr.ac.snu.hcil.android.common.view.container.decoration.HorizontalDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.externals.OTExternalService
import kr.ac.snu.hcil.omnitrack.core.externals.OTExternalServiceManager
import kr.ac.snu.hcil.omnitrack.ui.activities.OTFragment
import kr.ac.snu.hcil.omnitrack.ui.pages.home.MeasureFactoryAdapter
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Created by Young-Ho on 7/29/2016.
 */
class ServiceListFragment : OTFragment() {

    @Inject
    protected lateinit var externalServiceManager: OTExternalServiceManager

    private val adapter = Adapter()

    private val serviceList = ArrayList<OTExternalService>()

    private val activateColor: Int by lazy { ContextCompat.getColor(requireContext(), R.color.colorPointed) }

    private val deactivateColor: Int by lazy { ContextCompat.getColor(requireContext(), R.color.colorRed_Light) }

    private val onActivatingColor: Int by lazy { ContextCompat.getColor(requireContext(), R.color.material_grey_100) }

    private val internetRequiredAlertBuilder: MaterialDialog.Builder by lazy {
        DialogHelper.makeSimpleAlertBuilder(requireContext(), requireContext().resources.getString(R.string.msg_external_service_activation_requires_internet))
    }

    private val apiKeyChangedBroadcastReceiver: BroadcastReceiver by lazy {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == OTExternalServiceManager.BROADCAST_ACTION_SERVICE_API_KEYS_CHANGED) {
                    val newServiceList = externalServiceManager.availableServices
                    val diff = DiffUtil.calculateDiff(OTExternalService.ServiceListDiffCallback(serviceList, newServiceList))
                    serviceList.clear()
                    serviceList.addAll(externalServiceManager.availableServices)
                    diff.dispatchUpdatesTo(adapter)
                }
            }
        }
    }

    override fun onInject(app: OTAndroidApp) {
        app.applicationComponent.inject(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_home_services, container, false)

        rootView.ui_recyclerview.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
        //listView.addItemDecoration(HorizontalDividerItemDecoration(0, 20))

        rootView.ui_recyclerview.adapter = adapter

        if (BuildConfig.ENABLE_DYNAMIC_API_KEY_MODIFICATION == true) {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(apiKeyChangedBroadcastReceiver, OTExternalServiceManager.apiKeyChangedIntentFilter)
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (BuildConfig.ENABLE_DYNAMIC_API_KEY_MODIFICATION == true) {
            LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(apiKeyChangedBroadcastReceiver)
        }

        ui_recyclerview.adapter = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.serviceList.clear()
        this.serviceList.addAll(externalServiceManager.availableServices)
    }



    /*
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        println("permission result: $requestCode ${pendedActivations.size()}")
        if (pendedActivations[requestCode] != null) {
            if (grantResults.indexOf(PackageManager.PERMISSION_DENIED) == -1) {
                creationSubscriptions.add(
                        OTExternalService.availableServices[requestCode].startActivationActivityAsync(context).subscribe({
                            success ->
                            if (success) {
                                pendedActivations[requestCode].holderState = OTExternalService.ServiceState.ACTIVATED
                            } else {
                                pendedActivations[requestCode].holderState = OTExternalService.ServiceState.DEACTIVATED
                            }
                            pendedActivations.removeAt(requestCode)
                        }, { pendedActivations[requestCode].holderState = OTExternalService.ServiceState.DEACTIVATED }, { pendedActivations.removeAt(requestCode) })
                )
            } else {
                //activation failed.
                println("some permissions not granted. activation failed.")
                pendedActivations[requestCode].holderState = OTExternalService.ServiceState.DEACTIVATED
                pendedActivations.removeAt(requestCode)
            }
        }
    }*/

    private inner class Adapter : RecyclerView.Adapter<Adapter.ViewHolder>() {

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getService(position))
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.external_service_list_element, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return serviceList.size
        }

        private fun getService(position: Int): OTExternalService {
            return serviceList[position]
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val thumbView: ImageView
            val nameView: TextView
            val descriptionView: TextView

            val activationButton: AppCompatButton
            val progressBar: ProgressBar

            val measureFactoryListView: RecyclerView

            val measureFactoryAdapter = MeasureFactoryAdapter(context!!)

            var holderState: OTExternalService.ServiceState = OTExternalService.ServiceState.DEACTIVATED
                set(value) {
                    if (field != value) {
                        field = value
                        applyState(field)
                    }
                }

            init {
                thumbView = view.findViewById(R.id.thumb)
                nameView = view.findViewById(R.id.name)
                descriptionView = view.findViewById(R.id.description)

                progressBar = view.findViewById(R.id.ui_progress_bar)

                measureFactoryListView = view.findViewById(R.id.ui_supported_measure_list)
                measureFactoryListView.adapter = measureFactoryAdapter
                measureFactoryListView.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)
                measureFactoryListView.addItemDecoration(HorizontalDividerItemDecoration(ContextCompat.getColor(requireContext(), R.color.separator_Light), dipSize(requireContext(), 0.6f).roundToInt()))

                activationButton = view.findViewById(R.id.ui_button_activate)
                activationButton.background.setColorFilter(activateColor, PorterDuff.Mode.MULTIPLY)
                activationButton.setOnClickListener {
                    val service = getService(adapterPosition)

                    println("service state: ${service.state}")
                    when (service.state) {
                        OTExternalService.ServiceState.ACTIVATED -> {
                            creationSubscriptions.add(
                                    service.deactivate().subscribe({
                                        eventLogger.get().logServiceActivationChangeEvent(service.identifier, false)
                                    }, { err ->
                                        err.printStackTrace()
                                        Toast.makeText(requireContext(), "Deactivation was failed due to an error. Please Try again later.", Toast.LENGTH_LONG).show()
                                    })
                            )
                        }
                        OTExternalService.ServiceState.ACTIVATING -> {

                        }
                        OTExternalService.ServiceState.DEACTIVATED -> {
                            creationSubscriptions.add(
                                    Completable.defer {
                                        if (!service.isInternetRequiredForActivation) {
                                            Completable.complete()
                                        } else {
                                            ReactiveNetwork.checkInternetConnectivity()
                                                    .flatMapCompletable { connected ->
                                                        if (connected) {
                                                            Completable.complete()
                                                        } else {
                                                            Completable.error(IllegalStateException("No network connection"))
                                                        }
                                                    }
                                        }
                                    }.andThen(service.startActivationActivityAsync(requireActivity())).subscribe({ success ->
                                        if (success) {
                                            eventLogger.get().logServiceActivationChangeEvent(service.identifier, true)
                                        }
                                    }, { ex ->
                                        if (ex is IllegalStateException && ex.message?.contains("network", true) == true) {
                                            internetRequiredAlertBuilder.show()
                                        }
                                    })
                            )
                        }
                    }
                }
            }

            private fun applyState(state: OTExternalService.ServiceState) {
                TransitionManager.beginDelayedTransition(view as ViewGroup)
                when (state) {
                    OTExternalService.ServiceState.ACTIVATED -> {
                        progressBar.visibility = View.GONE
                        activationButton.setText(R.string.msg_deactivate)
                        activationButton.background.setColorFilter(deactivateColor, PorterDuff.Mode.MULTIPLY)
                        activationButton.isEnabled = true
                    }
                    OTExternalService.ServiceState.ACTIVATING -> {
                        progressBar.visibility = View.VISIBLE
                        activationButton.background.setColorFilter(onActivatingColor, PorterDuff.Mode.MULTIPLY)
                        activationButton.setText(R.string.msg_service_activating)
                        activationButton.isEnabled = false
                    }
                    OTExternalService.ServiceState.DEACTIVATED -> {
                        progressBar.visibility = View.GONE
                        activationButton.setText(R.string.msg_activate)
                        activationButton.background.setColorFilter(activateColor, PorterDuff.Mode.MULTIPLY)
                        activationButton.isEnabled = true
                    }
                }
            }

            fun bind(service: OTExternalService) {
                nameView.text = requireContext().resources.getString(service.nameResourceId)
                descriptionView.text = requireContext().resources.getString(service.descResourceId)
                thumbView.setImageResource(service.thumbResourceId)

                measureFactoryAdapter.service = service

                holderState = service.state

                creationSubscriptions.add(service.onStateChanged.subscribe {
                    state ->
                    holderState = state
                })
            }
        }
    }
}