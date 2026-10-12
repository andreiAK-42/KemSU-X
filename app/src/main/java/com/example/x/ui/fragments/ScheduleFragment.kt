package com.example.x.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.x.ui.screens.ScheduleScreen
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

class ScheduleFragment : Fragment() {
    private val vm: CoursesViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KemsuTheme {
                    ScheduleScreen(vm = vm, onBack = { findNavController().popBackStack() })
                }
            }
        }
    }
}
