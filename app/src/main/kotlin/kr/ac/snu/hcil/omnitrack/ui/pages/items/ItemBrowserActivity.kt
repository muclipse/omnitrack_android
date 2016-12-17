package kr.ac.snu.hcil.omnitrack.ui.pages.items

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.design.widget.BaseTransientBottomBar
import android.support.design.widget.CoordinatorLayout
import android.support.design.widget.Snackbar
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.*
import android.widget.*
import butterknife.bindView
import kr.ac.snu.hcil.omnitrack.OTApplication
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.OTItem
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.attributes.OTTimeAttribute
import kr.ac.snu.hcil.omnitrack.core.attributes.logics.AttributeSorter
import kr.ac.snu.hcil.omnitrack.core.attributes.logics.ItemComparator
import kr.ac.snu.hcil.omnitrack.core.datatypes.TimePoint
import kr.ac.snu.hcil.omnitrack.ui.DragItemTouchHelperCallback
import kr.ac.snu.hcil.omnitrack.ui.activities.OTTrackerAttachedActivity
import kr.ac.snu.hcil.omnitrack.ui.components.common.FallbackRecyclerView
import kr.ac.snu.hcil.omnitrack.ui.components.decorations.DrawableListBottomSpaceItemDecoration
import kr.ac.snu.hcil.omnitrack.ui.components.decorations.HorizontalDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.ui.components.decorations.HorizontalImageDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.utils.DialogHelper
import kr.ac.snu.hcil.omnitrack.utils.InterfaceHelper
import kr.ac.snu.hcil.omnitrack.utils.getDayOfMonth
import java.util.*

class ItemBrowserActivity : OTTrackerAttachedActivity(R.layout.activity_item_browser), AdapterView.OnItemSelectedListener, View.OnClickListener {

    companion object {
        fun makeIntent(tracker: OTTracker, context: Context): Intent {
            val intent = Intent(context, ItemBrowserActivity::class.java)
            intent.putExtra(OTApplication.INTENT_EXTRA_OBJECT_ID_TRACKER, tracker.objectId)
            return intent
        }
    }

    private val items = ArrayList<OTItem>()

    private val itemListView: FallbackRecyclerView by bindView(R.id.ui_item_list)

    private lateinit var itemListViewAdapter: ItemListViewAdapter

    private val emptyListMessageView: TextView by bindView(R.id.ui_empty_list_message)

    private val sortSpinner: Spinner by bindView(R.id.ui_spinner_sort_method)

    private val sortOrderButton: ToggleButton by bindView(R.id.ui_toggle_sort_order)

    private lateinit var removalSnackbar: Snackbar

    private var supportedItemComparators: List<ItemComparator>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sortSpinner.onItemSelectedListener = this

        rightActionBarButton?.visibility = View.VISIBLE
        rightActionBarButton?.setImageResource(R.drawable.ic_add_box)
        leftActionBarButton?.visibility = View.VISIBLE
        leftActionBarButton?.setImageResource(R.drawable.back_rhombus)

        itemListView.emptyView = emptyListMessageView
        itemListView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        itemListView.addItemDecoration(HorizontalImageDividerItemDecoration(context = this))
        itemListView.addItemDecoration(DrawableListBottomSpaceItemDecoration(R.drawable.expanded_view_inner_shadow_top, 0))

        itemListViewAdapter = ItemListViewAdapter()

        itemListView.adapter = itemListViewAdapter

        itemListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (removalSnackbar.isShown) {
                    removalSnackbar.dismiss()
                }
            }

        })


        ItemTouchHelper(DragItemTouchHelperCallback(itemListViewAdapter, this, false, true))
                .attachToRecyclerView(itemListView)


        sortOrderButton.setOnCheckedChangeListener { compoundButton, b ->
            if (b) {
                sortOrderButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ascending, 0)
            } else {
                sortOrderButton.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.descending, 0)
            }
        }
        InterfaceHelper.removeButtonTextDecoration(sortOrderButton)

        sortOrderButton.setOnClickListener(this)

        val snackBarContainer: CoordinatorLayout = findViewById(R.id.ui_snackbar_container) as CoordinatorLayout
        removalSnackbar = Snackbar.make(snackBarContainer, resources.getText(R.string.msg_item_removed_message), Snackbar.LENGTH_INDEFINITE)
        removalSnackbar.setAction(resources.getText(R.string.msg_undo)) {
            view ->
            itemListViewAdapter.undoRemoval()
        }.addCallback(object : BaseTransientBottomBar.BaseCallback<Snackbar>() {
            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                super.onDismissed(transientBottomBar, event)
                itemListViewAdapter.clearTrashcan()
            }

        })

    }

    override fun onTrackerLoaded(tracker: OTTracker) {
        super.onTrackerLoaded(tracker)
        items.clear()
        title = String.format(resources.getString(R.string.title_activity_item_browser, tracker.name))

        supportedItemComparators = tracker.getSupportedComparators()

        if (supportedItemComparators != null) {
            val adapter = ArrayAdapter<ItemComparator>(this, R.layout.simple_list_element_text_light, R.id.textView, supportedItemComparators)
            adapter.setDropDownViewResource(R.layout.simple_list_element_text_dropdown)

            sortSpinner.adapter = adapter
        }

        OTApplication.app.dbHelper.getItems(tracker, items)

        onItemListChanged()
    }

    override fun onPause() {
        super.onPause()
        itemListViewAdapter.clearTrashcan()
        removalSnackbar.dismiss()
    }

    override fun onClick(view: View) {
        reSort()
    }

    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, id: Long) {
        reSort()
    }

    private fun reSort(refresh: Boolean = true) {
        val comparator = supportedItemComparators?.get(sortSpinner.selectedItemPosition)
        if (comparator != null) {
            println("sort items by ${comparator}")
            comparator.isDecreasing = !sortOrderButton.isChecked
            items.sortWith(comparator)
            if (refresh)
                onItemListChanged()
        }
    }

    private fun getCurrentSort(): ItemComparator? {
        return supportedItemComparators?.get(sortSpinner.selectedItemPosition)
    }

    private fun onItemListChanged() {
        itemListViewAdapter.notifyDataSetChanged()
        onListChanged()
    }

    private fun onItemChanged(position: Int) {
        itemListViewAdapter.notifyItemChanged(position)
        onListChanged()
    }

    private fun onItemRemoved(position: Int) {
        itemListViewAdapter.notifyItemRemoved(position)
        onListChanged()
    }

    private fun onListChanged() {
    }

    override fun onToolbarLeftButtonClicked() {
        finish()
    }

    override fun onToolbarRightButtonClicked() {
        if (tracker != null) {
            val intent = ItemEditingActivity.makeIntent(tracker!!.objectId, this)
            intent.putExtra(OTApplication.INTENT_EXTRA_FROM, this@ItemBrowserActivity.javaClass.simpleName)
            startActivity(intent)
        }
    }

    fun deleteItemPermanently(position: Int): OTItem {
        val removedItem = items[position]
        OTApplication.app.dbHelper.removeItem(removedItem)
        items.removeAt(position)
        onItemRemoved(position)

        return removedItem
    }

    inner class ItemListViewAdapter : RecyclerView.Adapter<ItemListViewAdapter.ItemElementViewHolder>(), DragItemTouchHelperCallback.ItemDragHelperAdapter {

        private val removedItems = ArrayList<OTItem>()

        fun clearTrashcan() {
            for (item in removedItems) {
                OTApplication.app.dbHelper.removeItem(item)
            }
        }

        override fun onMoveItem(fromPosition: Int, toPosition: Int) {

        }

        override fun onRemoveItem(position: Int) {
            removedItems += items.removeAt(position)
            notifyItemRemoved(position)
            removalSnackbar.show()
        }

        fun undoRemoval() {
            if (!removedItems.isEmpty()) {
                val restored = removedItems.removeAt(removedItems.size - 1)
                items.add(restored)
                reSort(false)
                val newPosition = items.indexOf(restored)
                if (newPosition != -1) {
                    itemListViewAdapter.notifyItemInserted(newPosition)
                } else {
                    itemListViewAdapter.notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount(): Int {
            return items.size
        }

        override fun onBindViewHolder(holder: ItemElementViewHolder, position: Int) {
            holder.bindItem(items[position])
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemElementViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_list_element, parent, false)
            return ItemElementViewHolder(view)
        }

        inner class ItemElementViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener, PopupMenu.OnMenuItemClickListener {

            val colorBar: View by bindView(R.id.color_bar)

            val monthView: TextView by bindView(R.id.ui_text_month)
            val dayView: TextView by bindView(R.id.ui_text_day)

            val valueListView: RecyclerView by bindView(R.id.ui_list)

            val moreButton: View by bindView(R.id.ui_button_more)

            val sourceView: TextView by bindView(R.id.ui_logging_source)
            val loggingTimeView: TextView by bindView(R.id.ui_logging_time)

            val valueListAdapter: TableRowAdapter

            val itemMenu: PopupMenu


            init {

                val leftBar = view.findViewById(R.id.ui_left_bar)

                leftBar.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                view.minimumHeight = leftBar.measuredHeight

                /*
                if(tracker!=null)
                    colorBar.setBackgroundColor(tracker!!.color)
*/
                moreButton.setOnClickListener(this)

                itemMenu = PopupMenu(this@ItemBrowserActivity, moreButton, Gravity.TOP or Gravity.LEFT)
                itemMenu.inflate(R.menu.menu_item_list_element)
                itemMenu.setOnMenuItemClickListener(this)

                valueListView.layoutManager = LinearLayoutManager(this@ItemBrowserActivity, LinearLayoutManager.VERTICAL, false)

                valueListView.addItemDecoration(HorizontalDividerItemDecoration(ContextCompat.getColor(this@ItemBrowserActivity, R.color.separator_Light), (1 * resources.displayMetrics.density).toInt()))

                valueListAdapter = TableRowAdapter()
                valueListView.adapter = valueListAdapter
            }

            override fun onClick(p0: View?) {
                if (p0 === moreButton) {
                    itemMenu.show()
                    removalSnackbar.dismiss()
                }
            }

            override fun onMenuItemClick(p0: MenuItem): Boolean {
                when (p0.itemId) {
                    R.id.action_edit -> {
                        val intent = ItemEditingActivity.makeIntent(items[adapterPosition], tracker!!, this@ItemBrowserActivity)
                        intent.putExtra(OTApplication.INTENT_EXTRA_FROM, this@ItemBrowserActivity.javaClass.simpleName)
                        startActivity(intent)
                        return true
                    }
                    R.id.action_remove -> {
                        DialogHelper.makeYesNoDialogBuilder(this@ItemBrowserActivity, "OmniTrack", resources.getString(R.string.msg_item_remove_confirm), {
                            deleteItemPermanently(adapterPosition)
                        }).show()
                        return true
                    }
                }
                return false
            }


            fun bindItem(item: OTItem) {
                val cal = Calendar.getInstance()
                val currentSorter = getCurrentSort()

                cal.timeInMillis =
                        if (currentSorter is AttributeSorter && currentSorter.attribute is OTTimeAttribute) {
                            if (item.hasValueOf(currentSorter.attribute)) {
                                (item.getValueOf(currentSorter.attribute) as TimePoint).timestamp
                            } else item.timestamp
                        } else item.timestamp


                monthView.text = String.format(Locale.US, "%tb", cal);
                dayView.text = cal.getDayOfMonth().toString()

                sourceView.text = item.source.sourceText
                loggingTimeView.text = OTTimeAttribute.formats[OTTimeAttribute.GRANULARITY_MINUTE]!!.format(Date(item.timestamp))

                valueListAdapter.notifyDataSetChanged()
            }

            inner class TableRowAdapter : RecyclerView.Adapter<TableRowAdapter.TableRowViewHolder>() {

                fun getParentItem(): OTItem {
                    return items[this@ItemElementViewHolder.adapterPosition]
                }

                override fun getItemViewType(position: Int): Int {
                    if (this@ItemElementViewHolder.adapterPosition != -1 && getParentItem().hasValueOf(tracker!!.attributes[position]))
                        return tracker?.attributes?.get(position)?.getViewForItemListContainerType() ?: 0
                    else return 5
                }

                override fun onBindViewHolder(holder: TableRowViewHolder, position: Int) {
                    holder.bindAttribute(tracker!!.attributes[position])
                }

                override fun getItemCount(): Int {
                    return tracker?.attributes?.size ?: 0
                }

                override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableRowViewHolder {

                    val view = LayoutInflater.from(parent.context).inflate(when (viewType) {
                        OTAttribute.VIEW_FOR_ITEM_LIST_CONTAINER_TYPE_SINGLELINE -> R.layout.item_attribute_row_singleline
                        OTAttribute.VIEW_FOR_ITEM_LIST_CONTAINER_TYPE_MULTILINE -> R.layout.item_attribute_row_multiline
                        else -> R.layout.item_attribute_row_singleline
                    }, parent, false)

                    return if (viewType == 5) {
                        NoValueTableRowViewHolder(view as ViewGroup)
                    } else TableRowViewHolder(view as ViewGroup)
                }

                inner open class TableRowViewHolder(val view: ViewGroup) : RecyclerView.ViewHolder(view) {

                    val attributeNameView: TextView by bindView(R.id.ui_attribute_name)
                    var valueView: View


                    init {
                        valueView = view.findViewById(R.id.ui_value_view_replace)
                    }

                    open fun bindRawValue(name: String, value: String) {
                        attributeNameView.text = name
                        val newValueView: TextView = if (valueView is TextView) {
                            valueView as TextView
                        } else {
                            TextView(this@ItemBrowserActivity)
                        }

                        InterfaceHelper.setTextAppearance(newValueView, R.style.viewForItemListTextAppearance)

                        changeNewValueView(newValueView)

                        newValueView.text = value
                    }

                    open fun bindAttribute(attribute: OTAttribute<out Any>) {
                        attributeNameView.text = attribute.name
                        val sort = getCurrentSort()
                        attributeNameView.setTextColor(
                                if (sort is AttributeSorter && sort.attribute === attribute) {
                                    ContextCompat.getColor(this@ItemBrowserActivity, R.color.colorAccent)
                                } else ContextCompat.getColor(this@ItemBrowserActivity, R.color.textColorLight)
                        )

                        val newValueView = attribute.getViewForItemList(this@ItemBrowserActivity, valueView)
                        changeNewValueView(newValueView)

                        if (getParentItem().hasValueOf(attribute)) {
                            attribute.applyValueToViewForItemList(getParentItem().getValueOf(attribute), valueView)
                        }
                    }

                    private fun changeNewValueView(newValueView: View) {
                        if (newValueView !== valueView) {
                            val lp = valueView.layoutParams

                            val container = valueView.parent as ViewGroup

                            val index = container.indexOfChild(valueView)
                            container.removeView(valueView)
                            newValueView.layoutParams = lp
                            container.addView(newValueView, index)
                            valueView = newValueView
                        }
                    }
                }

                inner class NoValueTableRowViewHolder(view: ViewGroup) : TableRowViewHolder(view) {
                    init {
                        if (valueView is TextView) {
                            (valueView as TextView).text = getString(R.string.msg_empty_value)
                            (valueView as TextView).setTextColor(ContextCompat.getColor(this@ItemBrowserActivity, R.color.colorRed_Light))
                        }
                    }

                    override fun bindAttribute(attribute: OTAttribute<out Any>) {
                        attributeNameView.text = attribute.name
                    }
                }
            }
        }
    }

}
