package kr.ac.snu.hcil.omnitrack.ui.pages.trigger

import android.animation.ValueAnimator
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.os.Bundle
import android.support.design.widget.CoordinatorLayout
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.support.v7.app.AlertDialog
import android.support.v7.util.DiffUtil
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import com.beloo.widget.chipslayoutmanager.ChipsLayoutManager
import com.beloo.widget.chipslayoutmanager.SpacingItemDecoration
import com.github.salomonbrys.kotson.set
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import jp.wasabeef.recyclerview.animators.SlideInLeftAnimator
import kotlinx.android.synthetic.main.fragment_tracker_detail_triggers.*
import kotlinx.android.synthetic.main.layout_attached_tracker_list.view.*
import kotlinx.android.synthetic.main.trigger_list_element.view.*
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.analytics.IEventLogger
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.database.configured.models.OTTriggerDAO
import kr.ac.snu.hcil.omnitrack.core.system.OTExternalSettingsPrompter
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTriggerInformationHelper
import kr.ac.snu.hcil.omnitrack.core.triggers.TriggerFireBroadcastReceiver
import kr.ac.snu.hcil.omnitrack.ui.activities.OTFragment
import kr.ac.snu.hcil.omnitrack.ui.components.decorations.TopBottomHorizontalImageDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels.AManagedTriggerListViewModel
import kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels.ATriggerListViewModel
import kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels.OfflineTriggerListViewModel
import kr.ac.snu.hcil.omnitrack.ui.pages.trigger.viewmodels.TriggerViewModel
import kr.ac.snu.hcil.omnitrack.utils.DialogHelper
import kr.ac.snu.hcil.omnitrack.utils.IReadonlyObjectId
import kr.ac.snu.hcil.omnitrack.utils.InterfaceHelper
import kr.ac.snu.hcil.omnitrack.utils.dipRound
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.verticalMargin

/**
 * Created by younghokim on 2017. 10. 21..
 */
abstract class ATriggerListFragment<ViewModelType : ATriggerListViewModel> : OTFragment(), View.OnClickListener {
    companion object {
        // private const val STATE_EXPANDED_POSITION = "expandedTriggerPosition"
        //used when opending TriggerDetailActivity
        const val DETAIL_REQUEST_CODE = 12214

        const val VIEWTYPE_NORMAL = 0
        const val VIEWTYPE_GHOST = 1

        val switchColorFilter: ColorFilter by lazy { ColorMatrixColorFilter(ColorMatrix().apply { this.setSaturation(0.1f) }) }
    }

    private val settingsPrompter: OTExternalSettingsPrompter by lazy {
        OTExternalSettingsPrompter(this.requireContext())
    }

    private val triggerFireBroadcastReceiver: BroadcastReceiver by lazy {
        object : TriggerFireBroadcastReceiver() {
            override fun onTriggerFired(triggerId: String, triggerTime: Long) {
                currentTriggerViewModelList.find { it.dao.objectId == triggerId }?.onFired(triggerTime)
            }
        }
    }

    protected lateinit var viewModel: ViewModelType

    private val currentTriggerViewModelList = ArrayList<TriggerViewModel>()

    private val triggerListAdapter = TriggerListAdapter()

    private val triggerTypeDialog: AlertDialog by lazy {
        NewTriggerConditionTypeSelectionDialogHelper.builder(context!!, OTTriggerInformationHelper.getActionNameResId(viewModel.defaultTriggerInterfaceOptions.defaultActionType) ?: 0, viewModel.defaultTriggerInterfaceOptions.supportedConditionTypes) { type ->
            println("trigger type selected - $type")
            //viewModel.saveTrigger(makeNewDefaultTrigger(type))
            val defaultTrigger = makeNewDefaultTrigger(type)
            startActivityForResult(TriggerDetailActivity.makeNewTriggerIntent(context!!, configuredContext, defaultTrigger, viewModel.defaultTriggerInterfaceOptions), DETAIL_REQUEST_CODE)
            triggerTypeDialog.dismiss()
        }.create()
    }

    protected abstract fun initializeNewViewModel(savedInstanceState: Bundle?): Single<ViewModelType>

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        creationSubscriptions.add(
                initializeNewViewModel(savedInstanceState).doOnSuccess {
                    this.viewModel = it
                }.subscribe { model ->
                    onViewModelMounted(model, savedInstanceState)
                }
        )
    }

    protected open fun onViewModelMounted(viewModel: ViewModelType, savedInstanceState: Bundle?) {
        ui_trigger_list.emptyView = ui_empty_list_message.apply {
            setText(viewModel.emptyMessageResId)
        }

        ui_button_new_trigger.setOnClickListener(this)

        creationSubscriptions.add(
                viewModel.currentTriggerViewModelListObservable.subscribe { newList ->
                    val diffResult = DiffUtil.calculateDiff(IReadonlyObjectId.DiffUtilCallback(currentTriggerViewModelList, newList))

                    this.currentTriggerViewModelList.clear()
                    this.currentTriggerViewModelList.addAll(newList)

                    diffResult.dispatchUpdatesTo(triggerListAdapter)
                }
        )
    }

    override fun onClick(view: View) {
        if (view === ui_button_new_trigger) {
            val conditionTypes = viewModel.defaultTriggerInterfaceOptions.supportedConditionTypes
            if (conditionTypes?.size == 1) {
                //immediately create a trigger.
                startActivityForResult(TriggerDetailActivity.makeNewTriggerIntent(context!!, configuredContext, makeNewDefaultTrigger(conditionTypes.first()), viewModel.defaultTriggerInterfaceOptions), DETAIL_REQUEST_CODE)
            } else {
                //show dialog
                triggerTypeDialog.show()
            }
        }
    }

    protected open fun onProcessNewDefaultTrigger(dao: OTTriggerDAO) {
    }

    protected open fun makeNewDefaultTrigger(conditionType: Byte): OTTriggerDAO {
        val newDao = OTTriggerDAO()
        newDao.conditionType = conditionType
        onProcessNewDefaultTrigger(newDao)
        newDao.initialize()
        return newDao
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_tracker_detail_triggers, container, false)

        return rootView
    }

    override fun onStart() {
        super.onStart()
        LocalBroadcastManager.getInstance(act).registerReceiver(triggerFireBroadcastReceiver, TriggerFireBroadcastReceiver.makeIntentFilter())
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(act).unregisterReceiver(triggerFireBroadcastReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ui_trigger_list.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        val shadowDecoration = TopBottomHorizontalImageDividerItemDecoration(context = act)
        ui_trigger_list.addItemDecoration(shadowDecoration)

        (ui_trigger_list.layoutParams as CoordinatorLayout.LayoutParams).verticalMargin = -shadowDecoration.upperDividerHeight

        ui_trigger_list.itemAnimator = SlideInLeftAnimator(DecelerateInterpolator(2.0f))

        ui_trigger_list.adapter = triggerListAdapter

        setFloatingButtonColor(ContextCompat.getColor(act, R.color.colorPointed))
    }

    fun setFloatingButtonColor(color: Int) {
        ui_button_new_trigger.backgroundTintList = ColorStateList.valueOf(color)
        val hsv = floatArrayOf(0f, 0f, 0f)
        Color.colorToHSV(color, hsv)
        hsv[2] *= 0.8f
        ui_button_new_trigger.rippleColor = Color.HSVToColor(hsv)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == DETAIL_REQUEST_CODE && resultCode == Activity.RESULT_OK && data != null) {
            if (data.hasExtra(TriggerDetailActivity.INTENT_EXTRA_TRIGGER_DAO)) {
                val resultDao =
                        (act.application as OTApp).currentConfiguredContext.daoSerializationComponent.manager().parseTrigger(data.getStringExtra(TriggerDetailActivity.INTENT_EXTRA_TRIGGER_DAO))
                viewModel.addNewTrigger(resultDao)
            }
        }

        settingsPrompter.handleActivityResult(requestCode, resultCode, data)
    }

    inner class TriggerListAdapter : RecyclerView.Adapter<ATriggerViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ATriggerViewHolder {
            return when (viewType) {
                VIEWTYPE_GHOST -> {
                    TriggerViewHolder(parent)
                }
                VIEWTYPE_NORMAL -> {
                    TriggerViewHolder(parent)
                }
                else -> TriggerViewHolder(parent)
            }
        }

        override fun onBindViewHolder(holder: ATriggerViewHolder, position: Int) {
            holder.bind(getTriggerViewModelAt(position))
        }

        override fun getItemCount(): Int {
            return currentTriggerViewModelList.size
        }

        private fun getTriggerViewModelAt(position: Int): TriggerViewModel {
            return currentTriggerViewModelList[position]
        }

        override fun getItemViewType(position: Int): Int {
            return if (OTTriggerViewFactory.getConditionViewProvider(getTriggerViewModelAt(position).triggerConditionType.value
                            ?: -1) == null) {
                VIEWTYPE_GHOST
            } else VIEWTYPE_NORMAL
        }

    }

    abstract inner class ATriggerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(triggerViewModel: TriggerViewModel)
    }

    inner class TriggerViewHolder(parentView: ViewGroup?) : ATriggerViewHolder(LayoutInflater.from(context).inflate(R.layout.trigger_list_element, parentView, false)), View.OnClickListener {
        val subscriptions = CompositeDisposable()

        private var currentHeaderView: View? = null

        private var attachedViewModel: TriggerViewModel? = null

        private var attachedTrackerListView: View? = null

        private var currentAlertAnimation: ValueAnimator? = null

        private var attachedTrackerInfoList = ArrayList<OTTrackerDAO.SimpleTrackerInfo>()
        private val attachedTrackerListAdapter = AttachedTrackerAdapter()



        init {
            itemView.setOnClickListener(this)
            itemView.ui_trigger_switch.setOnClickListener(this)
            itemView.ui_trigger_switch.setOnCheckedChangeListener { sender, switched ->
                toggleColorFilter(switched)
            }
            itemView.ui_button_remove.setOnClickListener(this)

            setAttachedTrackerListViewVisibility(viewModel.defaultTriggerInterfaceOptions.showAttachedTrackers)
        }

        override fun onClick(view: View) {
            if (view === itemView) {
                val intent = if (viewModel is AManagedTriggerListViewModel) {
                    TriggerDetailActivity.makeEditTriggerIntent(context!!, attachedViewModel!!.objectId!!, viewModel.defaultTriggerInterfaceOptions)
                } else if (viewModel is OfflineTriggerListViewModel) {
                    TriggerDetailActivity.makeEditTriggerIntent(context!!, configuredContext, attachedViewModel!!.dao, viewModel.defaultTriggerInterfaceOptions)
                } else throw UnsupportedOperationException()

                startActivityForResult(intent, DETAIL_REQUEST_CODE)

            } else if (view === itemView.ui_trigger_switch) {
                attachedViewModel?.toggleSwitchAsync()
                        ?.let {
                            subscriptions.add(
                                    it.observeOn(AndroidSchedulers.mainThread()).subscribe({
                                        eventLogger.get().logTriggerChangeEvent(
                                                IEventLogger.SUB_EDIT, attachedViewModel?.objectId
                                        ) { content ->
                                            content[IEventLogger.CONTENT_KEY_PROPERTY] = "switch"
                                            attachedViewModel?.triggerSwitch?.value?.let {
                                                content[IEventLogger.CONTENT_KEY_NEWVALUE] = it
                                            }
                                        }
                                        if (attachedViewModel?.triggerSwitch?.value == true && attachedViewModel?.triggerConditionType?.value == OTTriggerDAO.CONDITION_TYPE_TIME) {
                                            if (!settingsPrompter.isBatteryOptimizationWhiteListed()) {
                                                settingsPrompter.askUserBatterOptimizationWhitelist()
                                            }
                                        }
                                        println("trigger switch was successfully changed.")
                                    }, { ex ->
                                        println("toggle trigger switch failed: ${ex.message}")
                                        ex.printStackTrace()
                                        if (ex is OTTriggerDAO.TriggerConfigInvalidException) {
                                            //TODO handle invalid UI effect

                                        }
                                    })
                            )
                        }
            } else if (view === itemView.ui_button_remove) {

                DialogHelper.makeNegativePhrasedYesNoDialogBuilder(act,
                        "OmniTrack",
                        String.format(OTApp.getString(R.string.msg_format_confirm_remove_trigger), OTApp.getString(OTTriggerInformationHelper.getActionNameResId(viewModel.defaultTriggerInterfaceOptions.defaultActionType) ?: 0)),
                        R.string.msg_remove, R.string.msg_cancel, {
                    attachedViewModel?.objectId?.let {
                        viewModel.removeTrigger(it)
                        eventLogger.get().logTriggerChangeEvent(
                                IEventLogger.SUB_REMOVE, attachedViewModel?.objectId
                        )
                    }
                }, onNo = null).show()


            }
        }

        override fun bind(triggerViewModel: TriggerViewModel) {
            subscriptions.clear()
            this.attachedViewModel = triggerViewModel

            subscriptions.add(
                    triggerViewModel.configDescResId.subscribe {
                        itemView.ui_type_description.setText(it)
                    }
            )

            subscriptions.add(
                    triggerViewModel.configIconResId.subscribe {
                        itemView.ui_type_icon.setImageResource(it)
                    }
            )

            subscriptions.add(
                    triggerViewModel.triggerSwitch.subscribe {
                        itemView.ui_trigger_switch.isChecked = it
                        toggleColorFilter(it)
                    }
            )

            subscriptions.add(
                    triggerViewModel.switchLocked.subscribe { locked ->
                        itemView.ui_trigger_switch.isEnabled = !locked

                        itemView.ui_trigger_switch.alpha = if (locked) {
                            0.2f
                        } else {
                            1.0f
                        }
                    }
            )

            subscriptions.add(
                    triggerViewModel.editionLocked.subscribe { locked ->
                        if (locked) {
                            itemView.setOnClickListener(null)
                        } else {
                            itemView.setOnClickListener(this)
                        }
                    }
            )

            subscriptions.add(
                    triggerViewModel.deletionLocked.subscribe { locked ->
                        itemView.ui_button_remove.isEnabled = !locked

                        itemView.ui_button_remove.alpha = if (locked) {
                            0.2f
                        } else {
                            1.0f
                        }
                    }
            )

            subscriptions.add(
                    triggerViewModel.attachedTrackers.subscribe { newList ->
                        refreshAttachedTrackerList(newList)
                    }
            )

            subscriptions.add(
                    triggerViewModel.scriptUsed.subscribe { used ->
                        itemView.ui_script_wappen.visibility = if (used) View.VISIBLE else View.INVISIBLE
                    }
            )

            subscriptions.add(
                    triggerViewModel.triggerCondition.subscribe { condition ->
                        println("condition changed")
                        triggerViewModel.triggerConditionType.value?.let { conditionType ->
                            val displayView = OTTriggerViewFactory.getConditionViewProvider(conditionType)?.getTriggerDisplayView(currentHeaderView, triggerViewModel.dao, act, configuredContext)
                            if (displayView != null) {
                                refreshHeaderView(displayView)
                            } else {
                                //unsupported displayview for this datum
                            }
                        }

                    }
            )

            subscriptions.add(
                    triggerViewModel.configSummary.subscribe { summary ->
                        itemView.ui_config_summary.text = summary
                    }
            )

            createViewSubscriptions.add(subscriptions)
        }

        private fun setAttachedTrackerListViewVisibility(visible: Boolean) {
            if (visible) {
                if (attachedTrackerListView == null) {
                    attachedTrackerListView = itemView.ui_attached_tracker_list_stub.inflate()
                            .apply {
                                ui_attached_tracker_list.adapter = attachedTrackerListAdapter
                                ui_attached_tracker_list.addItemDecoration(SpacingItemDecoration(dipRound(8), dipRound(10)))
                                ui_attached_tracker_list.layoutManager = ChipsLayoutManager.newBuilder(context)
                                        .setChildGravity(Gravity.CENTER_VERTICAL)
                                        .setOrientation(ChipsLayoutManager.HORIZONTAL)
                                        .build()
                            }
                } else {
                    attachedTrackerListView?.visibility = View.VISIBLE
                }
            } else {
                attachedTrackerListView?.visibility = View.GONE
            }
        }

        private fun refreshAttachedTrackerList(newList: List<OTTrackerDAO.SimpleTrackerInfo>) {
            if (viewModel.defaultTriggerInterfaceOptions.showAttachedTrackers) {

                val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return oldItemPosition == newItemPosition
                    }

                    override fun getOldListSize(): Int {
                        return attachedTrackerInfoList.size
                    }

                    override fun getNewListSize(): Int {
                        return newList.size
                    }

                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return attachedTrackerInfoList[oldItemPosition] == newList[newItemPosition]
                    }

                })

                attachedTrackerInfoList.clear()
                attachedTrackerInfoList.addAll(newList)

                if (attachedTrackerInfoList.isEmpty()) {
                    attachedTrackerListView?.ui_attached_tracker_list_empty_fallback?.visibility = View.VISIBLE
                    attachedTrackerListView?.ui_attached_tracker_list?.visibility = View.GONE
                } else {
                    attachedTrackerListView?.ui_attached_tracker_list_empty_fallback?.visibility = View.INVISIBLE
                    attachedTrackerListView?.ui_attached_tracker_list?.visibility = View.VISIBLE
                }

                diffResult.dispatchUpdatesTo(attachedTrackerListAdapter)
            }
        }

        private fun toggleColorFilter(switchOn: Boolean) {
            arrayOf(itemView.ui_wappen_type, itemView.ui_left, itemView.ui_script_wappen)
                    .forEach {
                        if (it != null) {
                            InterfaceHelper.setViewColorFilter(it, !switchOn, switchColorFilter, 0.65f)
                        }
                    }
        }

        private fun refreshHeaderView(headerView: View) {
            if (currentHeaderView !== headerView) {
                itemView.ui_header_view_container.removeAllViewsInLayout()
                itemView.ui_header_view_container.addView(headerView)
                currentHeaderView = headerView

                attachedViewModel?.getConditionViewModel()?.let {
                    val conditionType = attachedViewModel?.triggerConditionType?.value
                    if (conditionType != null) {
                        OTTriggerViewFactory.getConditionViewProvider(conditionType)?.connectViewModelToDisplayView(
                                it, headerView, subscriptions
                        )
                    }
                }
            }
        }

        inner class AttachedTrackerAdapter : RecyclerView.Adapter<AttachedTrackerViewHolder>() {
            override fun onBindViewHolder(holder: AttachedTrackerViewHolder, position: Int) {
                val info = attachedTrackerInfoList[position]
                // holder.itemView.color_bar.setBackgroundColor(info.first)
                holder.setColor(info.color)
                holder.setName(info.name)
            }

            override fun getItemCount(): Int {
                return attachedTrackerInfoList.size
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttachedTrackerViewHolder {
                return AttachedTrackerViewHolder(parent)
            }

        }

    }
}