package com.example.x.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.x.ui.screens.CourseDetailScreen
import com.example.x.ui.theme.KemsuTheme

class DetailFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val cId = arguments?.getString("cId") ?: ""
        return ComposeView(requireContext()).apply {
            setContent {
                KemsuTheme {
                    CourseDetailScreen(
                        cId = cId,
                        onBack = { findNavController().popBackStack()
                        }
                    )
                }
            }
        }
    }
}
