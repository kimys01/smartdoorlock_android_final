package com.example.smartdoorlock.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartdoorlock.R
import com.example.smartdoorlock.data.DoorLockLog

class DoorLogAdapter(private val logs: List<DoorLockLog>) :
    RecyclerView.Adapter<DoorLogAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textTimestamp: TextView = view.findViewById(R.id.textTimestamp)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textMethod: TextView = view.findViewById(R.id.textMethod)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_door_log, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = logs.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val log = logs[position]
        holder.textTimestamp.text = "🕒 시간: ${log.timestamp}"
        holder.textStatus.text = "🔐 상태: ${log.status}"
        holder.textMethod.text = "🔧 해제 방법: ${log.method ?: "알 수 없음"}"
    }
}
