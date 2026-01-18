package com.ff9.poweliftjudge

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ff9.poweliftjudge.database.Lift
import java.text.SimpleDateFormat
import java.util.*

class LiftAdapter(
    private var lifts: List<Lift>,
    private val onItemClick: (Lift) -> Unit,
    private val onEditClick: (Lift) -> Unit,
    private val onDeleteClick: (Lift) -> Unit
) : RecyclerView.Adapter<LiftAdapter.LiftViewHolder>() {

    class LiftViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val liftType: TextView = view.findViewById(R.id.tvLiftType)
        val liftDate: TextView = view.findViewById(R.id.tvLiftDate)
        val liftReps: TextView = view.findViewById(R.id.tvLiftReps)
        val liftStatus: ImageView = view.findViewById(R.id.ivStatus)
        val btnEdit: ImageView = view.findViewById(R.id.btnEdit)
        val btnDelete: ImageView = view.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LiftViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lift, parent, false)
        return LiftViewHolder(view)
    }

    override fun onBindViewHolder(holder: LiftViewHolder, position: Int) {
        val lift = lifts[position]
        holder.liftType.text = lift.type.uppercase()
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.liftDate.text = sdf.format(Date(lift.date))

        val repsText = if (lift.reps == 1) "1 rep" else "${lift.reps} reps"
        holder.liftReps.text = repsText

        holder.liftStatus.setImageResource(
            if (lift.valid) R.drawable.ic_check else R.drawable.ic_cross
        )

        holder.itemView.setOnClickListener { onItemClick(lift) }
        holder.btnEdit.setOnClickListener { onEditClick(lift) }
        holder.btnDelete.setOnClickListener { onDeleteClick(lift) }
    }

    override fun getItemCount(): Int = lifts.size

    fun updateData(newLifts: List<Lift>) {
        lifts = newLifts
        notifyDataSetChanged()
    }
}
