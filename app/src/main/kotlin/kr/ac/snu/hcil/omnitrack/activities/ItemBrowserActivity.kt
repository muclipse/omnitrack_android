package kr.ac.snu.hcil.omnitrack.activities

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.widget.PopupMenu
import android.widget.TextView
import kr.ac.snu.hcil.omnitrack.OmniTrackApplication
import kr.ac.snu.hcil.omnitrack.R
import kr.ac.snu.hcil.omnitrack.core.OTItem
import kr.ac.snu.hcil.omnitrack.core.OTTracker
import kr.ac.snu.hcil.omnitrack.core.attributes.OTAttribute
import kr.ac.snu.hcil.omnitrack.core.database.DatabaseHelper
import kr.ac.snu.hcil.omnitrack.ui.decorations.HorizontalDividerItemDecoration
import kr.ac.snu.hcil.omnitrack.utils.DialogHelper
import kr.ac.snu.hcil.omnitrack.utils.getDayOfMonth
import java.util.*

class ItemBrowserActivity : AppCompatActivity() {

    private var tracker: OTTracker? = null

    private val items = ArrayList<OTItem>()

    private lateinit var itemListView: RecyclerView

    private lateinit var itemListViewAdapter: ItemListViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_item_browser)

        itemListView = findViewById(R.id.ui_item_list) as RecyclerView

        itemListView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        itemListViewAdapter = ItemListViewAdapter()

        itemListView.adapter = itemListViewAdapter
    }

    override fun onStart() {
        super.onStart()

        items.clear()
        if (intent.getStringExtra(OmniTrackApplication.INTENT_EXTRA_OBJECT_ID_TRACKER) != null) {
            tracker = OmniTrackApplication.app.currentUser.trackers.filter { it.objectId == intent.getStringExtra(OmniTrackApplication.INTENT_EXTRA_OBJECT_ID_TRACKER) }.first()

            OmniTrackApplication.app.dbHelper.getItems(tracker!!, items)

            itemListViewAdapter.notifyDataSetChanged()
        }
    }

    inner class ItemListViewAdapter : RecyclerView.Adapter<ItemListViewAdapter.ItemElementViewHolder>() {
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

            val colorBar: View

            val monthView: TextView
            val dayView: TextView

            val valueListView: RecyclerView

            val moreButton: View

            val valueListAdapter: TableRowAdapter

            val itemMenu: PopupMenu


            init {
                colorBar = view.findViewById(R.id.color_bar)
                /*
                if(tracker!=null)
                    colorBar.setBackgroundColor(tracker!!.color)
*/
                monthView = view.findViewById(R.id.ui_text_month) as TextView
                dayView = view.findViewById(R.id.ui_text_day) as TextView

                moreButton = view.findViewById(R.id.ui_button_more)
                moreButton.setOnClickListener(this)

                itemMenu = PopupMenu(this@ItemBrowserActivity, moreButton, Gravity.TOP or Gravity.LEFT)
                itemMenu.inflate(R.menu.menu_item_list_element)
                itemMenu.setOnMenuItemClickListener(this)


                valueListView = view.findViewById(R.id.ui_list) as RecyclerView
                valueListView.layoutManager = LinearLayoutManager(this@ItemBrowserActivity, LinearLayoutManager.VERTICAL, false)

                valueListView.addItemDecoration(HorizontalDividerItemDecoration(resources.getColor(R.color.separator_Light, null), (1 * resources.displayMetrics.density).toInt()))

                valueListAdapter = TableRowAdapter()
                valueListView.adapter = valueListAdapter
            }

            override fun onClick(p0: View?) {
                if (p0 === moreButton) {
                    itemMenu.show()
                }
            }

            override fun onMenuItemClick(p0: MenuItem): Boolean {
                println(p0.itemId)
                when (p0.itemId) {
                    R.id.action_edit -> {
                        return true
                    }
                    R.id.action_remove -> {
                        DialogHelper.makeYesNoDialogBuilder(this@ItemBrowserActivity, "OmniTrack", resources.getString(R.string.msg_item_remove_confirm), {
                            OmniTrackApplication.app.dbHelper.deleteObjects(DatabaseHelper.ItemScheme, items[adapterPosition].dbId!!)
                            items.removeAt(adapterPosition)
                            itemListViewAdapter.notifyItemRemoved(adapterPosition)
                        }).show()
                        return true
                    }
                }
                return false
            }


            fun bindItem(item: OTItem) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = item.timestamp
                monthView.text = String.format(Locale.US, "%tb", cal);
                dayView.text = cal.getDayOfMonth().toString()

                valueListAdapter.notifyDataSetChanged()
            }

            inner class TableRowAdapter : RecyclerView.Adapter<TableRowAdapter.TableRowViewHolder>() {

                fun getParentItem(): OTItem {
                    return items[this@ItemElementViewHolder.adapterPosition]
                }

                override fun getItemViewType(position: Int): Int {
                    if (getParentItem().hasValueOf(tracker!!.attributes[position]))
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

                    val attributeNameView: TextView
                    var valueView: View

                    init {
                        attributeNameView = view.findViewById(R.id.ui_attribute_name) as TextView
                        valueView = view.findViewById(R.id.ui_value_view_replace)
                    }

                    open fun bindAttribute(attribute: OTAttribute<out Any>) {
                        attributeNameView.text = attribute.name

                        val newValueView = attribute.getViewForItemList(this@ItemBrowserActivity, valueView)
                        if (newValueView !== valueView) {
                            val lp = valueView.layoutParams
                            val index = view.indexOfChild(valueView)
                            view.removeView(valueView)
                            newValueView.layoutParams = lp
                            view.addView(newValueView, index)
                            valueView = newValueView
                        }

                        if (getParentItem().hasValueOf(attribute) ?: false) {
                            attribute.applyValueToViewForItemList(getParentItem().getValueOf(attribute), valueView)
                        }
                    }
                }

                inner class NoValueTableRowViewHolder(view: ViewGroup) : TableRowViewHolder(view) {
                    init {
                        if (valueView is TextView) {
                            (valueView as TextView).text = "No value"
                            (valueView as TextView).setTextColor(resources.getColor(R.color.colorRed_Light, null))
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
