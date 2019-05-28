package kr.ac.snu.hcil.omnitrack.ui.pages.home

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.transition.AutoTransition
import android.transition.Fade
import android.transition.TransitionSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import butterknife.bindView
import com.afollestad.materialdialogs.MaterialDialog
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.functions.BiFunction
import kotlinx.android.synthetic.main.fragment_recyclerview_and_fab.*
import kotlinx.android.synthetic.main.fragment_recyclerview_and_fab.view.*
import kotlinx.android.synthetic.main.tracker_list_element.view.*
import kr.ac.snu.hcil.android.common.time.TimeHelper
import kr.ac.snu.hcil.android.common.view.DialogHelper
import kr.ac.snu.hcil.android.common.view.IReadonlyObjectId
import kr.ac.snu.hcil.android.common.view.InterfaceHelper
import kr.ac.snu.hcil.android.common.view.container.adapter.FallbackRecyclerAdapterObserver
import kr.ac.snu.hcil.android.common.view.container.decoration.TopBottomHorizontalImageDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.BuildConfig
import kr.ac.snu.hcil.omnitrack.OTAndroidApp
import kr.ac.snu.hcil.omnitrack.OTApp
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.ItemLoggingSource
import kr.ac.snu.hcil.omnitrack.core.analytics.IEventLogger
import kr.ac.snu.hcil.omnitrack.core.auth.OTAuthManager
import kr.ac.snu.hcil.omnitrack.core.database.models.OTTrackerDAO
import kr.ac.snu.hcil.omnitrack.core.flags.F
import kr.ac.snu.hcil.omnitrack.core.system.OTAppFlagManager
import kr.ac.snu.hcil.omnitrack.core.triggers.OTReminderCommands
import kr.ac.snu.hcil.omnitrack.services.OTItemLoggingService
import kr.ac.snu.hcil.omnitrack.ui.activities.OTFragment
import kr.ac.snu.hcil.omnitrack.ui.components.tutorial.TutorialManager
import kr.ac.snu.hcil.omnitrack.ui.pages.items.ItemBrowserActivity
import kr.ac.snu.hcil.omnitrack.ui.pages.items.NewItemActivity
import kr.ac.snu.hcil.omnitrack.ui.pages.tracker.TrackerDetailActivity
import kr.ac.snu.hcil.omnitrack.ui.pages.visualization.ChartViewActivity
import org.jetbrains.anko.verticalMargin
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@Suppress("UNUSED_ANONYMOUS_PARAMETER")
/**
 * Created by Young-Ho Kim on 2016-07-18.
 */
class TrackerListFragment : OTFragment() {

    @Inject
    lateinit var authManager: OTAuthManager

    @Inject
    lateinit var tutorialManager: TutorialManager

    @Inject
    lateinit var appFlagManager: OTAppFlagManager

    private val trackerListAdapter: TrackerListAdapter = TrackerListAdapter()

    private lateinit var lastLoggedTimeFormat: SimpleDateFormat

    private lateinit var statHeaderSizeSpan: AbsoluteSizeSpan
    private lateinit var dateStyleSpan: StyleSpan
    private lateinit var statHeaderColorSpan: ForegroundColorSpan

    private lateinit var statContentStyleSpan: StyleSpan

    private var adapterObserver: FallbackRecyclerAdapterObserver? = null

    private var pendingNewTrackerId: String? = null

    private val emptyTrackerDialog: MaterialDialog.Builder by lazy {
        MaterialDialog.Builder(context!!)
                .cancelable(true)
                .positiveColorRes(R.color.colorPointed)
                .negativeColorRes(R.color.colorRed_Light)
                .title("OmniTrack")
                .content(R.string.msg_confirm_empty_tracker_log)
                .positiveText(R.string.msg_confirm_log)
                .negativeText(R.string.msg_cancel)
                .neutralText(R.string.msg_go_to_add_field)
    }

    private val newTrackerNameDialog: MaterialDialog.Builder by lazy {
        MaterialDialog.Builder(this.context!!)
                .title(R.string.msg_new_tracker_name)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .setSyncWithKeyboard(true)
                .inputRangeRes(1, 20, R.color.colorRed)
                .cancelable(true)
                .negativeText(R.string.msg_cancel)
    }

    private var permissionPromptingDialog: MaterialDialog? = null

    private val reminderCommands: OTReminderCommands by lazy {
        OTReminderCommands(requireContext())
    }

    private val collapsedHeight = 0
    private var expandedHeight: Int = 0

    private val currentTrackerViewModelList = ArrayList<TrackerListViewModel.TrackerInformationViewModel>()

    private lateinit var viewModel: TrackerListViewModel

    private val pendingPermissions = ArrayList<String>()

    companion object {
        const val CHANGE_TRACKER_SETTINGS = 0
        const val REMOVE_TRACKER = 1

        const val REQUEST_CODE_NEW_TRACKER = 1504

        const val STATE_EXPANDED_TRACKER_INDEX = "expandedTrackerIndex"

        //val transition = AutoTransition()
        val transition = TransitionSet()

        //val transition = ChangeBounds()

        init {
            //transition.addTransition(ChangeBounds())
            transition.addTransition(Fade())
            // transition.ordering = TransitionSet.ORDERING_TOGETHER

            transition.ordering = AutoTransition.ORDERING_TOGETHER
            transition.duration = 300
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_EXPANDED_TRACKER_INDEX, trackerListAdapter.currentlyExpandedIndex)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastLoggedTimeFormat = SimpleDateFormat(requireContext().resources.getString(R.string.msg_tracker_list_time_format))
        dateStyleSpan = StyleSpan(Typeface.NORMAL)
        statContentStyleSpan = StyleSpan(Typeface.BOLD)
        statHeaderSizeSpan = AbsoluteSizeSpan(requireContext().resources.getDimensionPixelSize(R.dimen.tracker_list_element_information_text_headerSize))
        statHeaderColorSpan = ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.textColorLight))
        //attach events
        // user.trackerAdded += onTrackerAddedHandler
        //  user.trackerRemoved += onTrackerRemovedHandler

        trackerListAdapter.currentlyExpandedIndex = savedInstanceState?.getInt(STATE_EXPANDED_TRACKER_INDEX, -1) ?: -1
    }

    override fun onInject(app: OTAndroidApp) {
        app.applicationComponent.inject(this)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        expandedHeight = resources.getDimensionPixelSize(R.dimen.button_height_normal)

        viewModel = ViewModelProviders.of(this).get(TrackerListViewModel::class.java)
        viewModel.userId = authManager.userId

        creationSubscriptions.add(
                viewModel.requiredPermissions.subscribe { permissions ->
                    appendNewPermissions(*permissions.filter { ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }.toTypedArray())
                }
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_recyclerview_and_fab, container, false)

        if (appFlagManager.flag(F.AddNewTracker)) {
            rootView.fab.setOnClickListener { view ->
                newTrackerNameDialog.input(null, viewModel.generateNewTrackerName(), false) { dialog, text ->
                    startActivityForResult(TrackerDetailActivity.makeNewTrackerIntent(text.toString(), requireContext()), REQUEST_CODE_NEW_TRACKER)

                }.show()
                //Toast.makeText(context,String.format(resources.getString(R.string.sentence_new_tracker_added), newTracker.name), Toast.LENGTH_LONG).show()
            }
        } else {
            rootView.fab.hide()
            rootView.fab.isEnabled = false
        }

        rootView.ui_empty_list_message.setText(R.string.msg_tracker_empty)

        adapterObserver = FallbackRecyclerAdapterObserver(rootView.ui_empty_list_message, trackerListAdapter)

        rootView.ui_recyclerview.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        val shadowDecoration = TopBottomHorizontalImageDividerItemDecoration(context = requireContext(), heightMultiplier = resources.getFraction(R.fraction.tracker_list_separator_height_ratio, 1, 1))
        rootView.ui_recyclerview.addItemDecoration(shadowDecoration)
        (rootView.ui_recyclerview.layoutParams as CoordinatorLayout.LayoutParams).verticalMargin = -shadowDecoration.upperDividerHeight
        rootView.ui_recyclerview.adapter = trackerListAdapter

        return rootView
    }

    private fun askPendingPermissions() {
        if (pendingPermissions.isNotEmpty()) {
            val rxPermissions = RxPermissions(this)
            this.startSubscriptions.add(
                    rxPermissions.request(*(pendingPermissions.toTypedArray())).subscribe { granted ->
                        if (granted)
                            println("permissions granted.")
                        else println("permissions not granted.")
                    }
            )
        }
    }

    private fun appendNewPermissions(vararg permissions: String) {
        permissions.forEach {
            if (!pendingPermissions.contains(it))
                pendingPermissions.add(it)
        }

        if (permissions.isNotEmpty()) {
            if (permissionPromptingDialog == null) {
                permissionPromptingDialog = DialogHelper.makeYesNoDialogBuilder(requireActivity(), resources.getString(R.string.msg_permission_required),
                        String.format(resources.getString(R.string.msg_permission_request_of_tracker)),
                        cancelable = false,
                        onYes = {
                            askPendingPermissions()
                        },
                        onCancel = null,
                        yesLabel = R.string.msg_allow_permission,
                        noLabel = R.string.msg_cancel
                ).build()
            }
            if (permissionPromptingDialog?.isShowing == false) {
                permissionPromptingDialog?.show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startSubscriptions.add(
                viewModel.trackerViewModels.subscribe { trackerViewModelList ->

                    val diffResult = DiffUtil.calculateDiff(
                            IReadonlyObjectId.DiffUtilCallback(currentTrackerViewModelList, trackerViewModelList)
                    )

                    currentTrackerViewModelList.clear()
                    currentTrackerViewModelList.addAll(trackerViewModelList)
                    diffResult.dispatchUpdatesTo(trackerListAdapter)

                    if (pendingNewTrackerId != null) {
                        val position = currentTrackerViewModelList.indexOfFirst { it._id == pendingNewTrackerId }
                        if (position != -1) {
                            view?.ui_recyclerview?.smoothScrollToPosition(position)
                        }
                        pendingNewTrackerId = null
                    }
                }
        )

        tutorialManager.checkAndShowTargetPrompt(TutorialManager.FLAG_TRACKER_LIST_ADD_TRACKER, true, this.requireActivity(), fab,
                R.string.msg_tutorial_add_tracker_primary,
                R.string.msg_tutorial_add_tracker_secondary,
                ContextCompat.getColor(requireContext(), R.color.colorPointed))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        for (viewHolder in trackerListAdapter.viewHolders) {
            viewHolder.subscriptions.clear()
        }
        trackerListAdapter.viewHolders.clear()

        ui_recyclerview.adapter = null

        adapterObserver?.dispose()
        adapterObserver = null

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_NEW_TRACKER) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    if (data.hasExtra(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER)) {
                        val newTrackerId = data.getStringExtra(OTApp.INTENT_EXTRA_OBJECT_ID_TRACKER)
                        pendingNewTrackerId = newTrackerId
                    }
                }
            }
        }
    }

    private fun handleTrackerClick(tracker: OTTrackerDAO) {
        if (tracker.isIndependentInputLocked() && !reminderCommands.isReminderPromptingToTracker(tracker._id!!)) {
            Toast.makeText(requireActivity(), "You cannot add new entry unless you are prompted by reminders.", Toast.LENGTH_LONG).show()
        } else {
            if (tracker.makeAttributesQuery(false, false).count() == 0L) {
                emptyTrackerDialog
                        .onPositive { materialDialog, dialogAction ->
                            activity?.startService(OTItemLoggingService.makeLoggingIntent(requireContext(), ItemLoggingSource.Manual, true, null, tracker._id!!))
                            //OTBackgroundLoggingService.log(context, tracker, OTItem.ItemLoggingSource.Manual, notify = false).subscribe()
                        }
                        .onNeutral { materialDialog, dialogAction ->
                            startActivity(TrackerDetailActivity.makeIntent(tracker._id, requireContext()))
                        }
                        .show()
            } else {
                startActivity(NewItemActivity.makeNewItemPageIntent(tracker._id!!, requireContext()))
            }
        }
    }

    inner class TrackerListAdapter : RecyclerView.Adapter<TrackerListAdapter.ViewHolder>() {

        val viewHolders = ArrayList<ViewHolder>()

        var currentlyExpandedIndex = -1
        private var lastExpandedViewHolder: ViewHolder? = null

        init {
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            try {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.tracker_list_element, parent, false)
                return ViewHolder(view).apply {
                    viewHolders.add(this)
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
                throw Exception("Inflation failed")
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindViewModel(currentTrackerViewModelList[position])
        }

        override fun getItemCount(): Int {
            return currentTrackerViewModelList.size
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }


        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {

            val name: TextView by bindView(R.id.name)
            val color: View by bindView(R.id.color_bar)
            val lockedIndicator: View by bindView(R.id.ui_locked_indicator)
            val expandButton: ImageButton by bindView(R.id.ui_expand_button)

            val lastLoggingTimeView: TextView by bindView(R.id.ui_last_logging_time)
            val todayLoggingCountView: TextView by bindView(R.id.ui_today_logging_count)
            val totalItemCountView: TextView by bindView(R.id.ui_total_item_count)

            val alarmIcon: View by bindView(R.id.alarm_icon)
            val alarmText: TextView by bindView(R.id.alarm_text)

            val expandedView: View by bindView(R.id.ui_expanded_view)

            val editButton: View by bindView(R.id.ui_button_edit)
            val listButton: View by bindView(R.id.ui_button_list)
            val removeButton: View by bindView(R.id.ui_button_remove)
            val chartViewButton: View by bindView(R.id.ui_button_charts)

            val errorIndicator: AppCompatImageButton by bindView(R.id.ui_invalid_icon)

            private var trackerId: String? = null

            var collapsed = true

            val expandedViewHeight: Int

            var subscriptions = CompositeDisposable()

            init {

                expandedView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                expandedViewHeight = expandedView.measuredHeight

                view.setOnClickListener(this)
                editButton.setOnClickListener(this)
                listButton.setOnClickListener(this)
                removeButton.setOnClickListener(this)
                chartViewButton.setOnClickListener(this)

                expandButton.setOnClickListener(this)

                errorIndicator.setOnClickListener(this)

                collapse(false)
            }

            private val collapseAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(p0: Animator?) {
                    }

                    override fun onAnimationEnd(p0: Animator?) {
                        expandButton.isEnabled = true
                        collapse(false)
                    }

                    override fun onAnimationCancel(p0: Animator?) {
                        collapse(false)
                    }

                    override fun onAnimationStart(p0: Animator?) {
                        expandButton.isEnabled = false
                        expandButton.setImageResource(R.drawable.more_horiz_scarse)
                    }

                })
                addUpdateListener {
                    val progress = (animatedValue as Float)
                    expandedView.alpha = progress
                    expandedView.layoutParams.apply { height = (collapsedHeight + (expandedHeight - collapsedHeight) * progress).toInt() }
                    itemView.requestLayout()
                }
            }

            private val expandAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250
                interpolator = DecelerateInterpolator()
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationRepeat(p0: Animator?) {
                    }

                    override fun onAnimationEnd(p0: Animator?) {
                        expandButton.isEnabled = true
                        expand(false)
                    }

                    override fun onAnimationCancel(p0: Animator?) {
                        expand(false)
                    }

                    override fun onAnimationStart(p0: Animator?) {
                        expandButton.isEnabled = false
                        expandButton.setImageResource(R.drawable.up_dark)
                        expandedView.visibility = View.VISIBLE
                        expandedView.layoutParams.height = 0
                        expandedView.requestLayout()
                    }

                })
                addUpdateListener {
                    val progress = (animatedValue as Float)
                    expandedView.alpha = progress
                    expandedView.layoutParams.apply { height = (collapsedHeight + (expandedHeight - collapsedHeight) * progress).toInt() }
                    itemView.requestLayout()
                }
            }

            override fun onClick(view: View) {
                val trackerViewModel = currentTrackerViewModelList[adapterPosition]
                if (view === itemView) {
                    handleTrackerClick(trackerViewModel.trackerDao)
                } else if (view === editButton) {
                    startActivity(TrackerDetailActivity.makeIntent(trackerId, this@TrackerListFragment.requireContext()))
                } else if (view === listButton) {
                    startActivity(ItemBrowserActivity.makeIntent(trackerId!!, this@TrackerListFragment.requireContext()))
                } else if (view === removeButton) {
                    DialogHelper.makeNegativePhrasedYesNoDialogBuilder(this@TrackerListFragment.requireContext(), trackerViewModel.trackerName.value
                            ?: "OmniTrack", getString(R.string.msg_confirm_remove_tracker), R.string.msg_remove,
                            onYes = { dialog ->
                                viewModel.removeTracker(trackerViewModel)
                                this@TrackerListFragment.view?.ui_recyclerview?.invalidateItemDecorations()
                                eventLogger.get().logTrackerChangeEvent(IEventLogger.SUB_REMOVE, trackerViewModel._id)
                            }).show()
                } else if (view === chartViewButton) {
                    startActivity(ChartViewActivity.makeIntent(trackerId!!, this@TrackerListFragment.requireContext()))


                } else if (view === expandButton) {
                    if (collapsed) {

                        lastExpandedViewHolder?.collapse(true)

                        currentlyExpandedIndex = adapterPosition
                        lastExpandedViewHolder = this
                        expand(true)

                    } else {
                        currentlyExpandedIndex = -1
                        lastExpandedViewHolder = null
                        collapse(true)
                    }

                }
            }

            private fun setLastLoggingTime(timestamp: Long?) {
                if (timestamp != null) {
                    InterfaceHelper.setTextAppearance(lastLoggingTimeView, R.style.trackerListInformationTextViewStyle)
                    val dateText = TimeHelper.getDateText(timestamp, requireContext()).toUpperCase()
                    val timeText = lastLoggedTimeFormat.format(Date(timestamp)).toUpperCase()
                    putStatistics(lastLoggingTimeView, dateText, timeText)
                    todayLoggingCountView.visibility = View.VISIBLE
                    totalItemCountView.visibility = View.VISIBLE

                } else {
                    lastLoggingTimeView.text = requireContext().resources.getString(R.string.msg_never_logged).toUpperCase()
                    InterfaceHelper.setTextAppearance(lastLoggingTimeView, R.style.trackerListInformationTextViewStyle_HeaderAppearance)
                    todayLoggingCountView.visibility = View.INVISIBLE
                    totalItemCountView.visibility = View.INVISIBLE
                }
            }

            private fun setTodayLoggingCount(count: Long) {
                val header = requireContext().resources.getString(R.string.msg_todays_log).toUpperCase()
                putStatistics(todayLoggingCountView, header, count.toString())
            }

            private fun setTotalItemCount(count: Long) {
                val header = resources.getString(R.string.msg_tracker_list_stat_total).toUpperCase()
                putStatistics(totalItemCountView, header, count.toString())
            }

            fun bindViewModel(viewModel: TrackerListViewModel.TrackerInformationViewModel) {

                trackerId = viewModel.trackerDao._id

                subscriptions.clear()
                subscriptions.add(
                        viewModel.trackerName.subscribe { nameText ->
                            name.text = nameText
                        }
                )

                subscriptions.add(
                        viewModel.isForExperiment.subscribe { isForExperiment ->
                            if (isForExperiment && BuildConfig.DEFAULT_EXPERIMENT_ID.isNullOrBlank()) {
                                itemView.ui_experiment_info.visibility = View.VISIBLE
                                itemView.ui_experiment_info.ui_experiment_name.text = "Research"
                            } else {
                                itemView.ui_experiment_info.visibility = View.GONE
                            }
                        }
                )

                subscriptions.add(
                        viewModel.experimentName.subscribe { (name) ->
                            if (name != null) {
                                itemView.ui_experiment_name.text = name
                            }
                        }
                )

                subscriptions.add(
                        viewModel.trackerColor.subscribe { colorInt ->
                            color.setBackgroundColor(colorInt)
                        }
                )

                subscriptions.add(
                        viewModel.isBookmarked.subscribe { isBookmarked ->
                            if (isBookmarked) {
                                itemView.ui_bookmark_indicator.visibility = View.VISIBLE
                            } else {
                                itemView.ui_bookmark_indicator.visibility = View.GONE
                            }
                        }
                )


                subscriptions.add(
                        Observable.combineLatest<Boolean, Boolean, Boolean>(
                                viewModel.trackerEditable.map { !it }, viewModel.trackerRemovable.map { !it }, BiFunction { editionLocked: Boolean, removalLocked: Boolean ->
                            editionLocked || removalLocked
                        }).subscribe { isLocked ->
                            lockedIndicator.visibility = if (isLocked) {
                                View.VISIBLE
                            } else View.GONE
                        }
                )


                subscriptions.add(
                        viewModel.trackerEditable.subscribe { editable ->

                            editButton.isEnabled = editable
                            editButton.alpha =
                                    if (editable) {
                                        1.0f
                                    } else {
                                        0.7f
                                    }
                        }
                )

                subscriptions.add(
                        viewModel.trackerRemovable.subscribe { editable ->
                            removeButton.isEnabled = editable
                            removeButton.alpha = if (editable) {
                                1.0f
                            } else {
                                0.7f
                            }
                        }
                )

                subscriptions.add(
                        viewModel.isItemListAllowed.subscribe { allowed ->
                            listButton.isEnabled = allowed
                            listButton.alpha = if (allowed) {
                                1.0f
                            } else {
                                0.7f
                            }
                        }
                )

                subscriptions.add(
                        viewModel.isVisualizationAllowed.subscribe { allowed ->
                            chartViewButton.isEnabled = allowed
                            chartViewButton.alpha = if (allowed) {
                                1.0f
                            } else {
                                0.7f
                            }
                        }
                )

                subscriptions.add(
                        viewModel.activeNotificationCount.subscribe { count ->
                            if (count > 0) {
                                alarmIcon.visibility = View.VISIBLE
                                alarmText.visibility = View.VISIBLE
                                alarmText.text = count.toString()
                            } else {
                                alarmIcon.visibility = View.INVISIBLE
                                alarmText.visibility = View.INVISIBLE
                            }

                        }
                )

                subscriptions.add(viewModel.lastLoggingTimeObservable.observeOn(AndroidSchedulers.mainThread()).subscribe { (time) -> setLastLoggingTime(time) })
                subscriptions.add(viewModel.todayCount.observeOn(AndroidSchedulers.mainThread()).subscribe { count -> setTodayLoggingCount(count) })
                subscriptions.add(viewModel.totalItemCount.observeOn(AndroidSchedulers.mainThread()).subscribe { count -> setTotalItemCount(count) })

                subscriptions.add(
                        viewModel.validationResult.subscribe { (isValid, invalidateMessages) ->
                            TooltipCompat.setTooltipText(errorIndicator, if (isValid) null else invalidateMessages?.joinToString("\n"))
                            errorIndicator.visibility = if (isValid) {
                                View.INVISIBLE
                            } else {
                                View.VISIBLE
                            }
                        }
                )

                if (currentlyExpandedIndex == adapterPosition) {
                    lastExpandedViewHolder = this
                    expand(false)
                } else {
                    collapse(false)
                }
            }


            private fun putStatistics(view: TextView, header: CharSequence, content: CharSequence) {
                val builder = SpannableString("$header\n$content").apply {
                    setSpan(statHeaderSizeSpan, 0, header.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(statHeaderColorSpan, 0, header.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(statContentStyleSpan, header.length + 1, header.length + 1 + content.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                view.text = builder
            }

            fun collapse(animate: Boolean) {
                if (animate) {
                    collapseAnimator.start()
                    collapsed = true

                } else {
                    expandedView.visibility = View.GONE
                    expandButton.setImageResource(R.drawable.more_horiz_scarse)
                    expandedView.layoutParams.apply { height = collapsedHeight }
                    collapsed = true
                }
            }

            fun expand(animate: Boolean) {
                if (animate) {
                    expandAnimator.start()
                    collapsed = false
                } else {
                    expandedView.visibility = View.VISIBLE
                    expandButton.setImageResource(R.drawable.up_dark)
                    expandedView.layoutParams.apply { height = expandedHeight }
                    collapsed = false
                }
            }
        }
    }
}