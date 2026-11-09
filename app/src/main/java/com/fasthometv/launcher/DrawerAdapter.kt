package com.fasthometv.launcher

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class DrawerOption(
    val iconRes: Int,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val badgeColor: Int = Color.parseColor("#38BDF8"),
    val iconTint: Int = Color.parseColor("#38BDF8"),
    val action: () -> Unit
)

class DrawerAdapter(
    private val onOptionClicked: (DrawerOption) -> Unit
) : RecyclerView.Adapter<DrawerAdapter.VH>() {

    private var items: List<DrawerOption> = emptyList()
    private var isLightMode: Boolean = false

    fun submit(list: List<DrawerOption>) {
        items = list
        notifyDataSetChanged()
    }

    fun setLightMode(light: Boolean) {
        if (isLightMode != light) {
            isLightMode = light
            notifyDataSetChanged()
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.drawerItemRoot)
        val iconCircle: View = v.findViewById(R.id.drawerIconCircle)
        val icon: ImageView = v.findViewById(R.id.drawerIcon)
        val title: TextView = v.findViewById(R.id.drawerTitle)
        val subtitle: TextView = v.findViewById(R.id.drawerSubtitle)
        val badge: TextView = v.findViewById(R.id.drawerBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_drawer_option, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.root.setBackgroundResource(
            if (isLightMode) R.drawable.bg_drawer_item_light else R.drawable.bg_drawer_item
        )
        h.icon.setImageResource(item.iconRes)
        h.title.text = item.title

        if (!item.subtitle.isNullOrBlank()) {
            h.subtitle.text = item.subtitle
            h.subtitle.visibility = View.VISIBLE
        } else {
            h.subtitle.visibility = View.GONE
        }

        if (!item.badge.isNullOrBlank()) {
            h.badge.text = item.badge
            h.badge.visibility = View.VISIBLE
        } else {
            h.badge.visibility = View.GONE
        }

        applyItemVisualState(h, item, h.root.isFocused)

        h.root.setOnClickListener {
            onOptionClicked(item)
        }
        h.root.setOnFocusChangeListener { _, focused ->
            applyItemVisualState(h, item, focused)
        }
    }

    private fun applyItemVisualState(h: VH, item: DrawerOption, focused: Boolean) {
        if (isLightMode) {
            // En Tema Claro: sin foco el fondo es blanco perla (texto oscuro), con foco es pizarra oscuro (texto blanco)
            h.title.setTextColor(if (focused) Color.WHITE else Color.parseColor("#0F172A"))
            h.subtitle.setTextColor(if (focused) Color.parseColor("#CBD5E1") else Color.parseColor("#475569"))
            h.iconCircle.setBackgroundResource(if (focused) R.drawable.bg_badge else R.drawable.bg_badge_light)
            h.badge.setBackgroundResource(if (focused) R.drawable.bg_badge else R.drawable.bg_badge_light)
            h.icon.setColorFilter(if (focused) Color.WHITE else Color.parseColor("#0F172A"))
            h.badge.setTextColor(if (focused) Color.WHITE else Color.parseColor("#0F172A"))
        } else {
            h.title.setTextColor(if (focused) Color.WHITE else Color.parseColor("#F1F5F9"))
            h.subtitle.setTextColor(if (focused) Color.parseColor("#CBD5E1") else Color.parseColor("#94A3B8"))
            h.iconCircle.setBackgroundResource(R.drawable.bg_badge)
            h.badge.setBackgroundResource(R.drawable.bg_badge)
            h.icon.setColorFilter(if (focused) Color.WHITE else item.iconTint)
            h.badge.setTextColor(item.badgeColor)
        }
    }
}
