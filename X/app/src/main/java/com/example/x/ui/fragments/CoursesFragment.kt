package com.example.x.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.x.ui.screens.CoursesScreen
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

class CoursesFragment : Fragment() {
    private val vm: CoursesViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KemsuTheme {
                    CoursesScreen(
                        vm = vm,
                        onCourseClick = { cId ->
                            val bundle = Bundle().apply { putString("cId", cId ?: "") }
                            findNavController().navigate(com.example.x.R.id.action_courses_to_detail, bundle)
                        },
                        onEventsClick = { findNavController().navigate(com.example.x.R.id.action_courses_to_events) },
                        onLoginClick = { findNavController().navigate(com.example.x.R.id.action_courses_to_login) },
                        onSettingsClick = { findNavController().navigate(com.example.x.R.id.action_courses_to_settings) }
                    )
                }
            }
        }
    }
}
