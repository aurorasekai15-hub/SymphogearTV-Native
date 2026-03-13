package com.symphogear.tv.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.symphogear.tv.R
import com.symphogear.tv.model.Category

class SidebarAdapter(
    private val categories: ArrayList<Category>?,
    private val onItemClick: (Category, Int) -> Unit
) : RecyclerView.Adapter<SidebarAdapter.ViewHolder>() {

    private var selectedPosition = 0

    // Map category name (lowercase) to emoji icon
    private val catIcons = mapOf(
        "nasional"      to "📺",
        "berita"        to "📰",
        "hiburan"       to "🎭",
        "olahraga"      to "⚽",
        "internasional" to "🌍",
        "jepang"        to "🗾",
        "vision+"       to "👁",
        "indihome"      to "🏠",
        "custom"        to "🔗",
        "favorit"       to "⭐",
        "favorite"      to "⭐"
    )

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val accent: View = view.findViewById(R.id.sidebar_accent)
        val icon: TextView = view.findViewById(R.id.sidebar_icon)
        val name: TextView = view.findViewById(R.id.sidebar_name)
        val count: TextView = view.findViewById(R.id.sidebar_count)
        val root: View = view.findViewById(R.id.sidebar_item_root)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sidebar, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cat = categories?.get(position) ?: return
        val catName = cat.name ?: ""
        val key = catName.lowercase().trim()

        // Find matching icon
        val icon = catIcons.entries.firstOrNull { key.contains(it.key) }?.value ?: "📡"
        holder.icon.text = icon

        // Show name without emoji if name already has emoji
        holder.name.text = catName

        // Count badge
        val count = cat.channels?.size ?: 0
        holder.count.text = if (count > 99) "99+" else count.toString()

        // Selected state
        val isSelected = position == selectedPosition
        holder.accent.visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
        holder.root.setBackgroundResource(
            if (isSelected) R.drawable.sidebar_item_selected_bg
            else android.R.color.transparent
        )
        holder.name.setTextColor(
            if (isSelected) holder.root.context.getColor(R.color.color_primary)
            else 0xE0F0E6FF.toInt()
        )

        holder.root.setOnClickListener {
            val prev = selectedPosition
            selectedPosition = holder.adapterPosition
            notifyItemChanged(prev)
            notifyItemChanged(selectedPosition)
            onItemClick(cat, selectedPosition)
        }
    }

    override fun getItemCount() = categories?.size ?: 0

    fun updateCategories(newCats: ArrayList<Category>?) {
        categories?.clear()
        newCats?.let { categories?.addAll(it) }
        notifyDataSetChanged()
    }

    fun selectCategory(position: Int) {
        val prev = selectedPosition
        selectedPosition = position
        notifyItemChanged(prev)
        notifyItemChanged(selectedPosition)
    }
}
