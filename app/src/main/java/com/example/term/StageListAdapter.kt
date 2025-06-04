package com.example.term

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StageListAdapter(
    private val items: List<String>,
    private val itemClickListener: (String) -> Unit
) : RecyclerView.Adapter<StageListAdapter.StageViewHolder>() {

    inner class StageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val stageNameTextView: TextView = itemView.findViewById(android.R.id.text1)

        init {
            itemView.setOnClickListener {
                itemClickListener(items[adapterPosition])
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return StageViewHolder(view)
    }

    override fun onBindViewHolder(holder: StageViewHolder, position: Int) {
        holder.stageNameTextView.text = items[position]
    }

    override fun getItemCount() = items.size
}
