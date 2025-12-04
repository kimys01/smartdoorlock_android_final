package com.example.smartdoorlock.ui.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartdoorlock.R

class DeviceAdapter(
    private val devices: List<Pair<BluetoothDevice, String>>,
    private val onClick: (BluetoothDevice, String) -> Unit
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>() {

    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.deviceName)
        val address: TextView = view.findViewById(R.id.deviceAddress)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device, parent, false)
        return DeviceViewHolder(view)
    }

    @SuppressLint("MissingPermission")
    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val (device, name) = devices[position]

        // 이름이 있으면 파란색으로 표시, 없으면 회색으로 "이름 없음" 표시
        if (name.isNotEmpty() && name != "이름 없음") {
            holder.name.text = name
            holder.name.setTextColor(Color.parseColor("#2196F3")) // 파란색
        } else {
            holder.name.text = "이름 없음"
            holder.name.setTextColor(Color.parseColor("#888888")) // 회색
        }

        holder.address.text = device.address

        holder.itemView.setOnClickListener {
            onClick(device, name)
        }
    }

    override fun getItemCount() = devices.size
}