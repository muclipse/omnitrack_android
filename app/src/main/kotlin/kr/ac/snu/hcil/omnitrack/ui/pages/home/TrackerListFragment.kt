package kr.ac.snu.hcil.omnitrack.ui.pages.home

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity.RESULT_OK
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.content.ContextCompat
import android.support.v7.widget.AppCompatImageButton
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
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
import butterknife.bindView
import com.afollestad.materialdialogs.MaterialDialog
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.OTItem
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.OTUser
import kr.ac.snu.hcil.omnitrack.core.database.DatabaseManager
import kr.ac.snu.hcil.omnitrack.core.database.EventLoggingManager
import kr.ac.snu.hcil.omnitrack.core.database.ItemCountTracer
import kr.ac.snu.hcil.omnitrack.core.triggers.OTTrigger
import kr.ac.snu.hcil.omnitrack.services.OTBackgroundLoggingService
import kr.ac.snu.hcil.omnitrack.ui.activities.OTActivity
import kr.ac.snu.hcil.omnitrack.ui.activities.OTFragment
import kr.ac.snu.hcil.omnitrack.ui.components.common.FallbackRecyclerView
import kr.ac.snu.hcil.omnitrack.ui.components.common.TooltipHelper
import kr.ac.snu.hcil.omnitrack.ui.components.decorations.DrawableListBottomSpaceItemDecoration
import kr.ac.snu.hcil.omnitrack.ui.components.decorations.HorizontalImageDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.ui.components.tutorial.TutorialManager
import kr.ac.snu.hcil.omnitrack.ui.pages.items.ItemBrowserActivity
import kr.ac.snu.hcil.omnitrack.ui.pages.items.ItemEditingActivity
import kr.ac.snu.hcil.omnitrack.ui.pages.tracker.TrackerDetailActivity
import kr.ac.snu.hcil.omnitrack.ui.pages.visualization.ChartViewActivity
import kr.ac.snu.hcil.omnitrack.utils.DialogHelper
import kr.ac.snu.hcil.omnitrack.utils.InterfaceHelper
import kr.ac.snu.hcil.omnitrack.utils.events.Event
import kr.ac.snu.hcil.omnitrack.utils.startActivityOnDelay
import kr.ac.snu.hcil.omnitrack.utils.time.TimeHelper
import rx.subscriptions.CompositeSubscription
import java.text.SimpleDateFormat
import java.util.*

/**
 * Created by Young-Ho Kim on 2016-07-18.
 */
class TrackerListFragment : OTFragment() {

    private lateinit var user: OTUser
    private var userLoaded: Boolean = false

    lateinit private var listView: FallbackRecyclerView

    lateinit private var emptyMessageView: TextView

    private lateinit var trackerListAdapter: TrackerListAdapter

    lateinit private var trackerListLayoutManager: LinearLayoutManager

    private lateinit var addTrackerFloatingButton: FloatingActionButton

    private lateinit var lastLoggedTimeFormat: SimpleDateFormat

    private lateinit var statHeaderSizeSpan: AbsoluteSizeSpan
    private lateinit var dateStyleSpan: StyleSpan
    private lateinit var statHeaderColorSpan: ForegroundColorSpan

    private lateinit var statContentStyleSpan: StyleSpan

    private val emptyTrackerDialog: MaterialDialog.Builder by lazy {
        MaterialDialog.Builder(context)
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
        MaterialDialog.Builder(this.context)
                .title(R.string.msg_new_tracker_name)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .setSyncWithKeyboard(true)
                .inputRangeRes(1, 20, R.color.colorRed)
                .cancelable(true)
                .negativeText(R.string.msg_cancel)
    }

    private val collapsedHeight = OTApplication.app.resourcesWrapped.getDimensionPixelSize(R.dimen.tracker_list_element_collapsed_height)
    private val expandedHeight = OTApplication.app.resourcesWrapped.getDimensionPixelSize(R.dimen.tracker_list_element_expanded_height)


    private val itemEventReceiver: BroadcastReceiver by lazy {
        ItemEventReceiver()
    }

    private val itemEventIntentFilter: IntentFilter by lazy {
        val filter = IntentFilter()
        filter.addAction(OTApplication.BROADCAST_ACTION_ITEM_ADDED)
        filter.addAction(OTApplication.BROADCAST_ACTION_ITEM_EDITED)
        filter.addAction(OTApplication.BROADCAST_ACTION_ITEM_REMOVED)

        filter
    }

    private val createViewSubscriptions: CompositeSubscription = CompositeSubscription()
    private val resumeSubscriptions: CompositeSubscription = CompositeSubscription()

    private val itemStatisticsDict = HashMap<String, ItemStatisticsUnit>()

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

        lastLoggedTimeFormat = SimpleDateFormat(context.resources.getString(R.string.msg_tracker_list_time_format))
        dateStyleSpan = StyleSpan(Typeface.NORMAL)
        statContentStyleSpan = StyleSpan(Typeface.BOLD)
        statHeaderSizeSpan = AbsoluteSizeSpan(context.resources.getDimensionPixelSize(R.dimen.tracker_list_element_information_text_headerSize))
        statHeaderColorSpan = ForegroundColorSpan(ContextCompat.getColor(context, R.color.textColorLight))
        //attach events
        // user.trackerAdded += onTrackerAddedHandler
        //  user.trackerRemoved += onTrackerRemovedHandler


        trackerListAdapter = TrackerListAdapter()
        trackerListAdapter.currentlyExpandedIndex = savedInstanceState?.getInt(STATE_EXPANDED_TRACKER_INDEX, -1) ?: -1
    }

    override fun onResume() {
        super.onResume()


        //context.registerReceiver(itemEventReceiver, itemEventIntentFilter)
        trackerListAdapter.notifyDataSetChanged()
        itemStatisticsDict.forEach { (key, tracer) -> tracer.register() }

    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater!!.inflate(R.layout.fragment_home_trackers, container, false)

        addTrackerFloatingButton = rootView.findViewById(R.id.fab) as FloatingActionButton
        addTrackerFloatingButton.setOnClickListener { view ->
            if (userLoaded) {
                newTrackerNameDialog.input(null, user.generateNewTrackerName(context), false) {
                    dialog, text ->
                    val newTracker = user.newTracker(text.toString(), true, isEditable = true)
                    startActivityForResult(TrackerDetailActivity.makeIntent(newTracker.objectId, context, true), REQUEST_CODE_NEW_TRACKER)

                }.show()
                //Toast.makeText(context,String.format(resources.getString(R.string.sentence_new_tracker_added), newTracker.name), Toast.LENGTH_LONG).show()
            }

        }

        listView = rootView.findViewById(R.id.ui_tracker_list_view) as FallbackRecyclerView
        emptyMessageView = rootView.findViewById(R.id.ui_empty_list_message) as TextView
        listView.emptyView = emptyMessageView
        trackerListLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        listView.layoutManager = trackerListLayoutManager

        listView.addItemDecoration(HorizontalImageDividerItemDecoration(R.drawable.horizontal_separator_pattern, context, resources.getFraction(R.fraction.tracker_list_separator_height_ratio, 1, 1)))
        listView.addItemDecoration(DrawableListBottomSpaceItemDecoration(R.drawable.expanded_view_inner_shadow_top, 0))

        listView.adapter = trackerListAdapter

        val activity = activity as? OTActivity
        if (activity != null) {
                createViewSubscriptions.add(
                        activity.signedInUserObservable.subscribe {
                            user ->
                            println("OMNITRACK trackerListFragment received user instance! : ${user.trackers.size} trackers")
                            this.user = user
                            userLoaded = true

                            itemStatisticsDict.forEach { (key, value) -> value.unregister() }
                            itemStatisticsDict.clear()
                            user.trackers.unObservedList.forEach {
                                tracker ->
                                itemStatisticsDict.put(tracker.objectId, ItemStatisticsUnit(tracker).apply { this.register() })
                            }

                            trackerListAdapter.notifyDataSetChanged()
                            createViewSubscriptions.add(
                                    this.user.trackerAdded.subscribe {
                                        trackerPair ->
                                        itemStatisticsDict.put(trackerPair.first.objectId, ItemStatisticsUnit(trackerPair.first).apply { this.register() })
                                        trackerListAdapter.notifyItemChanged(trackerListAdapter.itemCount - 1)
                                        trackerListAdapter.notifyItemInserted(trackerPair.second)
                                    }
                            )

                            createViewSubscriptions.add(
                                    this.user.trackerRemoved.subscribe {
                                        trackerPair ->
                                        itemStatisticsDict[trackerPair.first.objectId]?.unregister()
                                        itemStatisticsDict.remove(trackerPair.first.objectId)
                                        trackerListAdapter.notifyItemChanged(trackerListAdapter.itemCount - 1)
                                        trackerListAdapter.notifyItemRemoved(trackerPair.second)
                                    }
                            )
                        }
                )
        }

        return rootView
    }

    override fun onStart() {
        super.onStart()
        TutorialManager.checkAndShowTargetPrompt(TutorialManager.FLAG_TRACKER_LIST_ADD_TRACKER, true, this.activity, addTrackerFloatingButton,
                R.string.msg_tutorial_add_tracker_primary,
                R.string.msg_tutorial_add_tracker_secondary,
                ContextCompat.getColor(context, R.color.colorPointed))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        createViewSubscriptions.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_NEW_TRACKER) {
            if (resultCode == RESULT_OK) {
                if (data != null) {
                    if (data.hasExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER)) {
                        val newTrackerId = data.getStringExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER)
                        val activity = activity
                        if (activity != null) {
                            if (activity is OTActivity) {
                                resumeSubscriptions.add(
                                        activity.signedInUserObservable.subscribe {
                                            user ->
                                            val newTracker = user[newTrackerId]
                                            if (newTracker != null) {
                                                EventLoggingManager.logTrackerChangeEvent(EventLoggingManager.EVENT_NAME_CHANGE_TRACKER_ADD, newTracker)
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        println("tracker list pause")
        resumeSubscriptions.clear()
        for (viewHolder in trackerListAdapter.viewHolders) {
            viewHolder.subscriptions.clear()
        }
        trackerListAdapter.viewHolders.clear()

        //context.unregisterReceiver(itemEventReceiver)

        for (tracer in itemStatisticsDict) {
            tracer.value.unregister()
        }

        itemStatisticsDict
    }

    /*

    private val onTrackerAddedHandler = {
        sender: Any, args: ReadOnlyPair<OTTracker, Int> ->
        println("tracker added - ${args.second}")
        trackerListAdapter.notifyItemInserted(args.second)
        listView.scrollToPosition(args.second)
    }

    private val onTrackerRemovedHandler = {
        sender: Any, args: ReadOnlyPair<OTTracker, Int> ->
        println("tracker removed - ${args.second}")
        trackerListAdapter.notifyItemRemoved(args.second)
    }*/

    private fun handleTrackerClick(tracker: OTTracker) {
        if (tracker.attributes.size == 0) {
            emptyTrackerDialog
                    .onPositive { materialDialog, dialogAction ->
                        OTBackgroundLoggingService.log(context, tracker, OTItem.LoggingSource.Manual, notify = false).subscribe()
                    }
                    .onNeutral { materialDialog, dialogAction ->
                        startActivity(TrackerDetailActivity.makeIntent(tracker.objectId, context))
                    }
                    .show()
        } else {
            startActivityOnDelay(ItemEditingActivity.makeIntent(tracker.objectId, context))
        }
    }

    private fun handleTrackerLongClick(tracker: OTTracker) {
        /*
        val builder = AlertDialog.Builder(context)
        builder.setTitle(tracker.name)
        builder.setItems(popupMessages){
            dialog, which ->
            when(which) {
                CHANGE_TRACKER_SETTINGS -> {
                    val intent = Intent(context, TrackerDetailActivity::class.java)
                    intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER, tracker.objectId)
                    startActivityOnDelay(intent)

                }
                REMOVE_TRACKER -> DialogHelper.makeYesNoDialogBuilder(context, tracker.name, getString(R.string.msg_confirm_remove_tracker), {->user.trackers.remove(tracker)}).show()
            }
        }
        builder.show()*/
    }


    inner class TrackerListAdapter() : RecyclerView.Adapter<TrackerListAdapter.ViewHolder>() {

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
            } catch(ex: Exception) {
                ex.printStackTrace()
                throw Exception("Inflation failed")
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bindTracker(user.trackers[position])
        }

        override fun getItemCount(): Int {
            return if (userLoaded) user.trackers.size else 0
        }

        override fun getItemId(position: Int): Long {
            return position.toLong();
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

            private val validationErrorMessages = ArrayList<CharSequence>()

            var collapsed = true

            val expandedViewHeight: Int

            var subscriptions = CompositeSubscription()

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
                    val lp = itemView.layoutParams.apply { height = (collapsedHeight + (expandedHeight - collapsedHeight) * progress).toInt() }
                    itemView.layoutParams = lp
                    itemView.requestLayout()

                    expandedView.layoutParams.height = (0.5f + (expandedViewHeight) * progress).toInt()
                    expandedView.requestLayout()
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
                    val lp = itemView.layoutParams.apply { height = (collapsedHeight + (expandedHeight - collapsedHeight) * progress).toInt() }
                    itemView.layoutParams = lp
                    itemView.requestLayout()

                    expandedView.layoutParams.height = (0.5f + (expandedViewHeight) * progress).toInt()
                    expandedView.requestLayout()
                }
            }

            override fun onClick(view: View) {
                if (view === itemView) {
                    handleTrackerClick(user.trackers[adapterPosition])
                } else if (view === editButton) {
                    startActivityOnDelay(TrackerDetailActivity.makeIntent(user.trackers[adapterPosition].objectId, this@TrackerListFragment.context))
                } else if (view === listButton) {
                    startActivityOnDelay(ItemBrowserActivity.makeIntent(user.trackers[adapterPosition], this@TrackerListFragment.context))
                } else if (view === removeButton) {
                    val tracker = user.trackers[adapterPosition]
                    DialogHelper.makeNegativePhrasedYesNoDialogBuilder(context, tracker.name, getString(R.string.msg_confirm_remove_tracker), R.string.msg_remove, onYes= { ->
                        user.trackers.remove(tracker);
                        listView.invalidateItemDecorations();
                        EventLoggingManager.logTrackerChangeEvent(EventLoggingManager.EVENT_NAME_CHANGE_TRACKER_REMOVE, tracker)
                    }).show()
                } else if (view === chartViewButton) {
                    val tracker = user.trackers[adapterPosition]
                    startActivityOnDelay(ChartViewActivity.makeIntent(tracker.objectId, this@TrackerListFragment.context))


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

                } else if (view === errorIndicator) {
                    if (validationErrorMessages.size > 0) {
                        TooltipHelper.makeTooltipBuilder(adapterPosition, errorIndicator)
                                .text(
                                validationErrorMessages.joinToString("\n")
                                ).show()
                    }
                }
            }

            private fun setLastLoggingTime(timestamp: Long?) {
                if (timestamp != null) {
                    InterfaceHelper.setTextAppearance(lastLoggingTimeView, R.style.trackerListInformationTextViewStyle)
                    val dateText = TimeHelper.getDateText(timestamp, context).toUpperCase()
                    val timeText = lastLoggedTimeFormat.format(Date(timestamp)).toUpperCase()
                    putStatistics(lastLoggingTimeView, dateText, timeText)
                    todayLoggingCountView.visibility = View.VISIBLE
                    totalItemCountView.visibility = View.VISIBLE

                } else {
                    lastLoggingTimeView.text = context.resources.getString(R.string.msg_never_logged).toUpperCase()
                    InterfaceHelper.setTextAppearance(lastLoggingTimeView, R.style.trackerListInformationTextViewStyle_HeaderAppearance)
                    todayLoggingCountView.visibility = View.INVISIBLE
                    totalItemCountView.visibility = View.INVISIBLE
                }
            }

            private fun setTodayLoggingCount(count: Long) {
                val header = context.resources.getString(R.string.msg_todays_log).toUpperCase()
                putStatistics(todayLoggingCountView, header, count.toString())
            }

            private fun setTotalItemCount(count: Long) {
                val header = context.resources.getString(R.string.msg_tracker_list_stat_total).toUpperCase()
                putStatistics(totalItemCountView, header, count.toString())
            }


            fun bindTracker(tracker: OTTracker) {

                name.text = tracker.name
                color.setBackgroundColor(tracker.color)

                validationErrorMessages.clear()
                errorIndicator.visibility = if (tracker.isValid(validationErrorMessages)) {
                    View.INVISIBLE
                } else {
                    View.VISIBLE
                }

                if (tracker.isEditable) {
                    lockedIndicator.visibility = View.GONE
                    editButton.visibility = View.VISIBLE
                    removeButton.visibility = View.VISIBLE
                } else {
                    lockedIndicator.visibility = View.VISIBLE
                    editButton.visibility = View.GONE
                    removeButton.visibility = View.GONE
                }

                subscriptions.clear()

                /*
                subscriptions.add(
                        DatabaseManager.getItemListSummary(tracker).subscribe {
                            summary ->
                            setLastLoggingTime(summary.lastLoggingTime)
                            setTodayLoggingCount(summary.todayCount ?: 0)
                            setTotalItemCount(summary.totalCount ?: 0)
                        }
                )*/

                /*
                subscriptions.add(
                        DatabaseManager.getTotalItemCount(tracker).subscribe{
                            count->
                            setTotalItemCount(count ?: 0)
                        }
                )*/
                val itemStatisticsUnit = itemStatisticsDict[tracker.objectId]
                if (itemStatisticsUnit != null) {
                    itemStatisticsUnit.onTotalItemCountChanged.clear()
                    itemStatisticsUnit.onLastLoggingTimeChanged.clear()
                    itemStatisticsUnit.onTodayCountChanged.clear()

                    itemStatisticsUnit.refresh()

                    itemStatisticsUnit.onTotalItemCountChanged += {
                        sender, count ->
                        setTotalItemCount(count ?: 0)
                    }
                    setTotalItemCount(itemStatisticsUnit.totalItemCount ?: 0)


                    itemStatisticsUnit.onLastLoggingTimeChanged += {
                        sender, time ->
                        setLastLoggingTime(time)
                    }
                    setLastLoggingTime(itemStatisticsUnit.lastLoggingTime)

                    itemStatisticsUnit.onTodayCountChanged += {
                        sender, count ->
                        setTodayLoggingCount(count ?: 0)
                    }
                    setTodayLoggingCount(itemStatisticsUnit.todayCount ?: 0)
                }

                subscriptions.add(
                        tracker.colorChanged.subscribe {
                            args ->
                            color.setBackgroundColor(args.second)
                        }
                )

                subscriptions.add(
                        tracker.nameChanged.subscribe {
                            args ->
                            name.text = args.second
                        }
                )

                val activeNotificationTriggers = tracker.owner?.triggerManager?.getAttachedTriggers(tracker, OTTrigger.ACTION_NOTIFICATION)?.filter { it.isOn == true }
                if (activeNotificationTriggers?.isNotEmpty() == true) {

                    alarmIcon.visibility = View.VISIBLE
                    alarmText.visibility = View.VISIBLE

                    alarmText.setText(activeNotificationTriggers.size.toString())
                } else {
                    alarmIcon.visibility = View.INVISIBLE
                    alarmText.visibility = View.INVISIBLE
                }

                /*
                subscriptions.add(
                        DatabaseManager.getLastLoggingTime(tracker).observeOn(AndroidSchedulers.mainThread()).subscribe {
                            timestamp ->

                    }
                )


                subscriptions.add(
                        DatabaseManager.getLogCountOfDay(tracker).observeOn(AndroidSchedulers.mainThread()).subscribe {
                            count ->
                            println("log count of day: $count")
                            val header = context.resources.getString(R.string.msg_todays_log).toUpperCase()
                            putStatistics(todayLoggingCountView, header, count.toString())
                        }
                )

                subscriptions.add(
                        DatabaseManager.getTotalItemCount(tracker).observeOn(AndroidSchedulers.mainThread()).subscribe {
                            count ->

                            println("log count total: $count")
                            val header = context.resources.getString(R.string.msg_tracker_list_stat_total).toUpperCase()
                            putStatistics(totalItemCountView, header, count.toString())
                        }
                )

                */



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
                    val lp = itemView.layoutParams.apply { height = collapsedHeight }
                    itemView.layoutParams = lp
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
                    val lp = itemView.layoutParams.apply { height = expandedHeight }
                    itemView.layoutParams = lp
                    collapsed = false
                }
            }
        }
    }

    inner class ItemEventReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (userLoaded) {
                val tracker = user[intent.getStringExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER)]
                if (tracker != null) {
                    val trackerPosition = user.trackers.indexOf(tracker)
                    println("tracker changed = $trackerPosition")
                    trackerListAdapter.notifyItemChanged(trackerPosition)
                }
            }
        }

    }

    class ItemStatisticsUnit(val tracker: OTTracker) {
        var totalItemCount: Long? = null
            set(value) {
                if (field != value) {
                    field = value
                    onTotalItemCountChanged.invoke(this, value)
                }
            }

        var todayCount: Long? = null
            set(value) {
                if (field != value) {
                    field = value
                    onTodayCountChanged.invoke(this, value)
                }
            }

        var lastLoggingTime: Long? = null
            set(value) {
                if (field != value) {
                    field = value
                    onLastLoggingTimeChanged.invoke(this, value)
                }
            }

        val countTracer: ItemCountTracer = ItemCountTracer(tracker)

        val subscriptions = CompositeSubscription()

        val onTotalItemCountChanged = Event<Long?>()
        val onTodayCountChanged = Event<Long?>()
        val onLastLoggingTimeChanged = Event<Long?>()

        init {
        }

        fun refresh() {
            countTracer.notifyConnected()
        }

        fun register() {
            subscriptions.add(
                    countTracer.itemCountObservable.subscribe {
                        count ->
                        totalItemCount = count
                    }
            )

            subscriptions.add(
                    DatabaseManager.getLogCountOfDay(tracker).subscribe {
                        count ->
                        todayCount = count
                    }
            )

            subscriptions.add(
                    DatabaseManager.getLastLoggingTime(tracker).subscribe {
                        time ->
                        lastLoggingTime = time
                    }
            )

            countTracer.register()
            countTracer.notifyConnected()
        }

        fun unregister() {
            if (countTracer.isRegistered)
                countTracer.unregister()

            subscriptions.clear()
        }
    }
}