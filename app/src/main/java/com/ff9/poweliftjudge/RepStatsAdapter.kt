package com.ff9.poweliftjudge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class RepStats(
    val descentTime: Long,
    val ascentTime: Long,
    val totalTime: Long
)

class RepStatsAdapter(private val repStats: List<RepStats>) :
    RecyclerView.Adapter<RepStatsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvRepNumber: TextView = view.findViewById(R.id.tvRepNumber)
        val tvDescentTime: TextView = view.findViewById(R.id.tvDescentTime)
        val tvAscentTime: TextView = view.findViewById(R.id.tvAscentTime)
        val tvTotalRepTime: TextView = view.findViewById(R.id.tvTotalRepTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_rep_stats, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stats = repStats[position]
        holder.tvRepNumber.text = "Rep ${position + 1}"
        holder.tvDescentTime.text = "↓ ${String.format("%.1fs", stats.descentTime / 1000.0)}"
        holder.tvAscentTime.text = "↑ ${String.format("%.1fs", stats.ascentTime / 1000.0)}"
        holder.tvTotalRepTime.text = String.format("%.1fs", stats.totalTime / 1000.0)
    }

    override fun getItemCount() = repStats.size
}
