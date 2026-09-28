package com.example.x.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.example.x.ui.screens.LoginScreen
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

class LoginFragment : Fragment() {
    private val vm: CoursesViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                KemsuTheme {
                    LoginScreen(
                        vm = vm,
                        onLoginSuccess = { findNavController().navigate(com.example.x.R.id.action_login_to_courses) },
                        onBack = { /* login — стартовый, назад некуда */ }
                    )
                }
            }
        }
    }
}
