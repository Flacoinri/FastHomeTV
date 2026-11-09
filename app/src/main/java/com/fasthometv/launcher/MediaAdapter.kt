package com.fasthometv.launcher

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class MediaItem(
    val id: String,
    val title: String,
    val meta: String,
    val tag: String,
    val category: String,
    val imageUrl: String,
    val isHeroWide: Boolean,
    val fallbackColorStart: Int,
    val fallbackColorEnd: Int,
    var bitmap: Bitmap? = null
)

class MediaAdapter(
    private val onClick: (MediaItem) -> Unit,
    private val onFocused: (MediaItem) -> Unit
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private var items: List<MediaItem> = emptyList()
    private var lightMode: Boolean = false

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return items[position].id.hashCode().toLong()
    }

    fun submit(list: List<MediaItem>) {
        val sameIds = items.size == list.size && items.indices.all { items[it].id == list[it].id }
        items = list
        if (!sameIds) {
            notifyDataSetChanged()
        } else {
            notifyItemRangeChanged(0, items.size, "ARTWORK")
        }
    }

    fun notifyArtworkLoaded(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            notifyItemChanged(idx, "ARTWORK")
        }
    }

    fun indexOf(item: MediaItem): Int {
        return items.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
    }

    fun setLightMode(isLight: Boolean) {
        if (lightMode != isLight) {
            lightMode = isLight
            notifyItemRangeChanged(0, items.size, "THEME")
        }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val root: View = v.findViewById(R.id.mediaRoot)
        val posterBox: View = v.findViewById(R.id.posterBox)
        val posterImage: ImageView = v.findViewById(R.id.posterImage)
        val mediaTag: TextView = v.findViewById(R.id.mediaTag)
        val mediaFocusBorder: View = v.findViewById(R.id.mediaFocusBorder)
        val mediaTitle: TextView = v.findViewById(R.id.mediaTitle)
        val mediaMeta: TextView = v.findViewById(R.id.mediaMeta)

        init {
            posterBox.clipToOutline = true
            mediaFocusBorder.isDuplicateParentStateEnabled = true
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_media_card, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(h: VH, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            val item = items[position]
            if (item.bitmap != null) {
                h.posterImage.setImageBitmap(item.bitmap)
            }
            val titleFocused = if (lightMode) Color.parseColor("#0F172A") else Color.WHITE
            val titleUnfocused = if (lightMode) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
            val metaColor = if (lightMode) Color.parseColor("#475569") else Color.parseColor("#94A3B8")
            h.mediaTitle.setTextColor(if (h.root.isFocused) titleFocused else titleUnfocused)
            h.mediaMeta.setTextColor(metaColor)
            return
        }
        super.onBindViewHolder(h, position, payloads)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val density = h.itemView.resources.displayMetrics.density

        val widthDp = if (item.isHeroWide || position == 0) 345 else 142
        val heightDp = 196
        val widthPx = (widthDp * density).toInt()
        val heightPx = (heightDp * density).toInt()

        val boxLp = h.posterBox.layoutParams
        boxLp.width = widthPx
        boxLp.height = heightPx
        h.posterBox.layoutParams = boxLp

        val rootLp = h.root.layoutParams ?: RecyclerView.LayoutParams(
            widthPx + (16 * density).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rootLp.width = widthPx + (16 * density).toInt()
        h.root.layoutParams = rootLp

        h.mediaTitle.text = item.title
        h.mediaMeta.text = item.meta
        h.mediaTag.text = item.tag

        val titleFocused = if (lightMode) Color.parseColor("#0F172A") else Color.WHITE
        val titleUnfocused = if (lightMode) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
        val metaColor = if (lightMode) Color.parseColor("#475569") else Color.parseColor("#94A3B8")

        h.mediaTitle.setTextColor(if (h.root.isFocused) titleFocused else titleUnfocused)
        h.mediaMeta.setTextColor(metaColor)

        if (item.bitmap != null) {
            h.posterImage.setImageBitmap(item.bitmap)
        } else {
            h.posterImage.setImageDrawable(
                GradientDrawable(
                    GradientDrawable.Orientation.TL_BR,
                    intArrayOf(item.fallbackColorStart, item.fallbackColorEnd)
                )
            )
        }

        h.root.setOnClickListener { onClick(item) }
        h.root.setOnFocusChangeListener { _, focused ->
            val s = if (focused) 1.04f else 1f
            h.posterBox.elevation = if (focused) 18f else 0f
            val fCol = if (lightMode) Color.parseColor("#0F172A") else Color.WHITE
            val uCol = if (lightMode) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0")
            h.mediaTitle.setTextColor(if (focused) fCol else uCol)
            h.posterBox.animate().scaleX(s).scaleY(s).setDuration(130).start()
            if (focused) {
                onFocused(item)
            }
        }
    }
}
