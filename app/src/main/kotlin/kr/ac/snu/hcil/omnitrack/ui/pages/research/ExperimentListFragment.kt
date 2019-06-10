package kr.ac.snu.hcil.omnitrack.ui.pages.research

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import kotlinx.android.synthetic.main.experiment_list_element.view.*
import kotlinx.android.synthetic.main.fragment_recyclerview_and_fab.view.*
import kr.ac.snu.hcil.android.common.time.TimeHelper
import kr.ac.snu.hcil.android.common.view.container.adapter.FallbackRecyclerAdapterObserver
import kr.ac.snu.hcil.android.common.view.container.decoration.TopBottomHorizontalImageDividerItemDecoration
import kr.ac.snu.hcil.android.common.view.inflateContent
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.database.models.research.ExperimentInfo
import kr.ac.snu.hcil.omnitrack.ui.activities.OTFragment
import org.jetbrains.anko.verticalMargin
import java.util.*

/**
 * Created by younghokim on 2018. 1. 4..
 */
class ExperimentListFragment : OTFragment() {

    companion object {
        const val VIEWTYPE_ACTIVE_EXPERIMENT = 0
        const val VIEWTYPE_DROPPED_EXPERIMENT = 1
    }

    private lateinit var viewModel: ResearchViewModel

    private val currentExperimentInfoList = ArrayList<ExperimentInfo>()

    private val invitationCodeInputDialog: MaterialDialog.Builder by lazy {
        MaterialDialog.Builder(this.context!!)
                .title(R.string.msg_insert_invitation_code)
                .inputType(InputType.TYPE_CLASS_TEXT)
                .setSyncWithKeyboard(true)
                .inputRangeRes(1, 30, R.color.colorRed)
                .cancelable(true)
                .negativeText(R.string.msg_cancel)
    }

    private val dropoutConfirmDialog: MaterialDialog.Builder by lazy {
        MaterialDialog.Builder(this.context!!)
                .title("Withdraw")
                .content("Withdraw from the experiment and remove the data regarding the experiment?")
                .inputType(InputType.TYPE_CLASS_TEXT)
                .setSyncWithKeyboard(true)
                .inputRangeRes(0, 150, R.color.colorRed)
                .cancelable(true)
                .negativeText(R.string.msg_cancel)
    }

    private val adapter = ExperimentListAdapter()
    private var adapterObserver: FallbackRecyclerAdapterObserver? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val rootView = inflater.inflate(R.layout.fragment_recyclerview_and_fab, container, false)

        rootView.ui_empty_list_message.setText(R.string.msg_empty_experiments)
        this.adapterObserver = FallbackRecyclerAdapterObserver(rootView.ui_empty_list_message, this.adapter)

        rootView.ui_recyclerview.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, false)

        rootView.ui_recyclerview.adapter = this.adapter

        val shadowDecoration = TopBottomHorizontalImageDividerItemDecoration(context = requireContext(), heightMultiplier = 0.8f)
        rootView.ui_recyclerview.addItemDecoration(shadowDecoration)
        (rootView.ui_recyclerview.layoutParams as CoordinatorLayout.LayoutParams).verticalMargin = -shadowDecoration.upperDividerHeight

        rootView.fab.setOnClickListener {
            invitationCodeInputDialog.input("Paste invitation code", null, false) { dialog, invitationCode ->
                viewModel.insertInvitationCode(invitationCode.toString())
            }.show()
        }

        return rootView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        adapterObserver?.dispose()
        adapterObserver = null
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(activity!!).get(ResearchViewModel::class.java)
        creationSubscriptions.add(
                viewModel.experimentListSubject.subscribe { experiments ->
                    val diffResult = DiffUtil.calculateDiff(ExperimentInfo.DiffUtilCallback(currentExperimentInfoList, experiments))
                    currentExperimentInfoList.clear()
                    currentExperimentInfoList.addAll(experiments)
                    diffResult.dispatchUpdatesTo(adapter)
                }
        )
    }

    open class ExperimentViewHolder(parent: ViewGroup, @LayoutRes layoutId: Int) : RecyclerView.ViewHolder(
            parent.inflateContent(layoutId, false)
    ) {
        fun setName(name: String) {
            itemView.name.text = name
        }

        open fun bind(experiment: ExperimentInfo) {
            setName(experiment.name)
        }
    }

    inner class ActiveExperimentViewHolder(parent: ViewGroup) : ExperimentViewHolder(parent, R.layout.experiment_list_element) {

        private var experimentId: String = ""
        override fun bind(experiment: ExperimentInfo) {
            super.bind(experiment)
            experimentId = experiment.id
            itemView.description.text = String.format(itemView.resources.getString(R.string.msg_format_experiment_joined_at), TimeHelper.FORMAT_YYYY_MM_DD.format(Date(experiment.joinedAt)))
        }

        init {
            itemView.btn_withdraw.setOnClickListener {
                dropoutConfirmDialog.input("Reasons to withdraw", null, true) { dialog, reason ->
                    viewModel.withdrawFromExperiment(experimentId, reason?.toString())
                }.show()
            }
        }
    }

    class DroppedExperimentViewHolder(parent: ViewGroup) : ExperimentViewHolder(parent, R.layout.experiment_list_element_dropped) {

        override fun bind(experiment: ExperimentInfo) {
            super.bind(experiment)
            itemView.description.text = String.format(itemView.resources.getString(R.string.msg_format_experiment_dropped_at), TimeHelper.FORMAT_YYYY_MM_DD.format(Date(experiment.droppedAt!!)))
        }
    }

    inner class ExperimentListAdapter : RecyclerView.Adapter<ExperimentViewHolder>() {

        override fun getItemViewType(position: Int): Int {
            return if (currentExperimentInfoList[position].droppedAt != null) {
                VIEWTYPE_DROPPED_EXPERIMENT
            } else VIEWTYPE_ACTIVE_EXPERIMENT
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExperimentViewHolder {
            return when (viewType) {
                VIEWTYPE_ACTIVE_EXPERIMENT -> ActiveExperimentViewHolder(parent)
                VIEWTYPE_DROPPED_EXPERIMENT -> DroppedExperimentViewHolder(parent)
                else -> throw Exception("Unsupported viewtype")
            }
        }

        override fun onBindViewHolder(holder: ExperimentViewHolder, position: Int) {
            val experiment = currentExperimentInfoList[position]
            holder.bind(experiment)
        }

        override fun getItemCount(): Int {
            return currentExperimentInfoList.size
        }

    }
}