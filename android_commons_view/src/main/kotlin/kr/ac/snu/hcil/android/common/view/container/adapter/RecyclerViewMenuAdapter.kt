package kr.ac.snu.hcil.android.common.view.container.adapter

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import kr.ac.snu.hcil.android.common.view.InterfaceHelper
import kr.ac.snu.hcil.android.common.view.R
import kr.ac.snu.hcil.android.common.view.container.viewholder.SimpleIconNameDescriptionViewHolder
import kr.ac.snu.hcil.android.common.view.inflateContent

/**
 * Created by younghokim on 2017. 3. 6..
 */
abstract class RecyclerViewMenuAdapter : RecyclerView.Adapter<RecyclerViewMenuAdapter.MenuViewHolder>() {
    abstract fun getMenuItemAt(index: Int): MenuItem

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        holder.bind(getMenuItemAt(position))
    }

    protected open fun getLayout(viewType: Int): Int {
        return R.layout.simple_menu_element_with_icon_title_description
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        return MenuViewHolder(parent.inflateContent(getLayout(viewType), false))
    }

    data class MenuItem(var iconRes: Int?, var name: String?, var description: String?, var onClick: () -> Unit, var isEnabled: Boolean = true)

    class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {

        var menuItem: MenuItem? = null
        val wrapped = SimpleIconNameDescriptionViewHolder(view)

        init {
            view.setOnClickListener(this)
        }

        override fun onClick(clicked: View) {
            if (clicked === this.itemView && this.menuItem?.isEnabled == true) {
                menuItem?.onClick?.invoke()
            }
        }


        fun bind(item: MenuItem) {
            menuItem = item
            if (item.iconRes == null) {
                wrapped.iconView.visibility = View.GONE
            } else {
                wrapped.iconView.visibility = View.VISIBLE
                wrapped.setIconDrawable(item.iconRes!!)
            }

            if (item.description == null) {
                wrapped.descriptionView.visibility = View.GONE
            } else {
                wrapped.descriptionView.visibility = View.VISIBLE
                wrapped.descriptionView.text = item.description
            }

            wrapped.nameView.text = item.name

            if (item.isEnabled) {
                wrapped.descriptionView.alpha = 1f
                wrapped.nameView.alpha = 1f
                wrapped.iconView.alpha = 1f
                itemView.isClickable = true
            } else {
                println("disable button.")
                wrapped.descriptionView.alpha = InterfaceHelper.ALPHA_INACTIVE
                wrapped.nameView.alpha = 0.2f
                wrapped.iconView.alpha = 0.2f
                itemView.isClickable = false
            }
        }
    }
}