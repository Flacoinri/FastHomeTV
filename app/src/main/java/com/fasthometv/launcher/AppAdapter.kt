package com.fasthometv.launcher

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class AppEntry(
    val pkg: String,
    val label: String,
    val icon: Drawable,
    val banner: Drawable?,
    val launch: Intent,
    val accentColor: Int,
    val isFav: Boolean = false
)

/**
 * fixedWidthPx > 0 -> ancho fijo (fila horizontal del Dock). 0 -> ancho de columna (grilla).
 */
class AppAdapter(
    private val fixedWidthPx: Int,
    private val showPinBadge: Boolean,
    private val onClick: (AppEntry) -> Unit,
    private val onLong: (AppEntry) -> Unit,
    private val onFocused: (AppEntry) -> Unit
) : RecyclerView.Adapter<AppAdapter.VH>() {

    private var items: List<AppEntry> = emptyList()
    private var lightMode: Boolean = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].pkg.hashCode().toLong()
    }

    fun submit(list: List<AppEntry>) {
        val samePkgs = items.size == list.size && items.indices.all {
            items[it].pkg == list[it].pkg && items[it].isFav == list[it].isFav
        }
        items = list
        if (!samePkgs) {
            notifyDataSetChanged()
        }
    }

    fun setLightMode(isLight: Boolean) {
        if (lightMode != isLight) {
            lightMode = isLight
            notifyItemRangeChanged(0, items.size, "THEME")
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: View = v.findViewById(R.id.card)
        val tileBox: View = v.findViewById(R.id.tileBox)
        val focusBorder: View = v.findViewById(R.id.focusBorder)
        val icon: ImageView = v.findViewById(R.id.icon)
        val banner: ImageView = v.findViewById(R.id.banner)
        val pinBadge: TextView = v.findViewById(R.id.pinBadge)
        val label: TextView = v.findViewById(R.id.label)

        init {
            tileBox.clipToOutline = true
            focusBorder.isDuplicateParentStateEnabled = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        if (fixedWidthPx > 0) {
            v.layoutParams = RecyclerView.LayoutParams(
                fixedWidthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val app = items[position]

        if (app.banner != null) {
            h.banner.setImageDrawable(app.banner)
            h.banner.visibility = View.VISIBLE
            h.icon.visibility = View.GONE
        } else {
            h.icon.setImageDrawable(app.icon)
            h.icon.visibility = View.VISIBLE
            h.banner.visibility = View.GONE
        }

        h.pinBadge.visibility = if (showPinBadge && app.isFav) View.VISIBLE else View.GONE
        h.label.text = app.label

        val unfocusedColor = if (lightMode) Color.parseColor("#334155") else Color.parseColor("#94A3B8")
        val focusedColor = if (lightMode) Color.parseColor("#0F172A") else Color.WHITE
        h.label.setTextColor(if (h.card.isFocused) focusedColor else unfocusedColor)

        h.card.setOnClickListener { onClick(app) }
        h.card.setOnLongClickListener {
            onLong(app)
            true
        }
        h.card.setOnFocusChangeListener { _, focused ->
            val s = if (focused) 1.07f else 1f
            h.tileBox.elevation = if (focused) 16f else 0f
            val fColor = if (lightMode) Color.parseColor("#0F172A") else Color.WHITE
            val uColor = if (lightMode) Color.parseColor("#334155") else Color.parseColor("#94A3B8")
            h.label.setTextColor(if (focused) fColor else uColor)
            h.tileBox.animate().scaleX(s).scaleY(s).setDuration(120).start()
            if (focused) {
                onFocused(app)
            }
        }
    }
}
