package com.lhacenmed.khatmah.ui.page.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.lhacenmed.khatmah.R

class TodayFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_today, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.btn_theme_settings).setOnClickListener {
            findNavController().navigate(R.id.action_today_to_themeSettings)
        }
        view.findViewById<View>(R.id.btn_language).setOnClickListener {
            findNavController().navigate(R.id.action_today_to_language)
        }
    }
}