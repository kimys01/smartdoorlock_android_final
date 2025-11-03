package com.example.smartdoorlock.ui.notifications

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.smartdoorlock.data.DoorLockLog
import com.example.smartdoorlock.databinding.FragmentNotificationsBinding
import com.google.firebase.database.*

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var database: DatabaseReference
    private lateinit var adapter: DoorLogAdapter
    private val logList = mutableListOf<DoorLockLog>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences("login_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("saved_id", null)

        if (userId == null) {
            Toast.makeText(context, "사용자 ID를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        adapter = DoorLogAdapter(logList)
        binding.recyclerViewLogs.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewLogs.adapter = adapter

        database = FirebaseDatabase.getInstance().getReference("doorlock_logs").child(userId)

        loadLogs()
    }

    private fun loadLogs() {
        binding.textViewEmpty.visibility = View.GONE
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                logList.clear()
                for (child in snapshot.children) {
                    val log = child.getValue(DoorLockLog::class.java)
                    log?.let { logList.add(it) }
                }

                if (logList.isEmpty()) {
                    binding.textViewEmpty.visibility = View.VISIBLE
                }

                // 최신 로그가 위로 오도록 정렬
                logList.sortByDescending { it.timestamp }

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "데이터 로드 실패: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
