package kr.ac.snu.hcil.android.common.view.container.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import kr.ac.snu.hcil.android.common.view.R

/**
 * Created by younghokim on 16. 8. 24..
 */
class IconNameEntryArrayAdapter(context: Context, objects: Array<out Entry>) : ArrayAdapter<IconNameEntryArrayAdapter.Entry>(context, R.layout.simple_list_element_icon_name, objects) {

    data class Entry(val iconId: Int, val nameId: Int)

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {

        return getView(position, convertView, parent)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {

        val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.simple_list_element_icon_name, parent, false)

        if (view.tag !is ViewHolder) {
            view.tag = ViewHolder(view)
        }

        val holder = view.tag as ViewHolder

        val entry = getItem(position)
        if (entry != null) {
            holder.iconView.setImageResource(entry.iconId)
            holder.nameView.setText(entry.nameId)
        }
        return view
    }

    class ViewHolder(val view: View) {
        val iconView: AppCompatImageView = view.findViewById(R.id.ui_icon)
        val nameView: TextView = view.findViewById(R.id.ui_name)
    }
}