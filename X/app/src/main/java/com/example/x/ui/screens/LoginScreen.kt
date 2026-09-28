package com.example.x.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.x.ui.theme.KemsuTheme
import com.example.x.viewmodel.CoursesViewModel

@Composable
fun LoginScreen(vm: CoursesViewModel, onLoginSuccess: () -> Unit, onBack: () -> Unit) {
    var user by remember { mutableStateOf(vm.savedLogin()) }
    var pass by remember { mutableStateOf(vm.savedPassword()) }
    var err by remember { mutableStateOf<String?>(null) }
    var rememberMe by remember { mutableStateOf(vm.hasSaved()) }
    val state by vm.uiState.collectAsState()

    LoginContent(
        user = user, pass = pass,
        onUser = { user = it }, onPass = { pass = it },
        rememberMe = rememberMe, onRemember = { rememberMe = it },
        isLoading = state.isLoading, error = err ?: state.error,
        onLogin = {
            err = null
            vm.login(user, pass, rememberMe) { ok, msg ->
                if (ok) onLoginSuccess() else err = msg
            }
        },
        onLogout = {
            vm.logoutFull {
                user = ""; pass = ""; rememberMe = false; err = null
            }
        },
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginContent(
    user: String, pass: String,
    onUser: (String) -> Unit, onPass: (String) -> Unit,
    rememberMe: Boolean, onRemember: (Boolean) -> Unit,
    isLoading: Boolean, error: String?,
    onLogin: () -> Unit, onLogout: () -> Unit, onBack: () -> Unit
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Вход") }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
            Text("Вход в ЭИОС", style = MaterialTheme.typography.titleLarge)
            Text("Введите логин и пароль. При успешной проверке они подставятся автоматически при следующем заходе.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(value = user, onValueChange = onUser, label = { Text("Логин") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = pass, onValueChange = onPass, label = { Text("Пароль") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = rememberMe, onCheckedChange = onRemember)
                Text("Запомнить меня")
            }
            if (isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("Войти") }
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) { Text("Выйти из аккаунта (стереть всё)") }
            }
        }
    }
}

@Preview(showBackground = true, name = "Login")
@Composable
fun LoginPreview() {
    KemsuTheme { LoginContent(user = "ivanov", pass = "12345", onUser = {}, onPass = {}, rememberMe = true, onRemember = {}, isLoading = false, error = null, onLogin = {}, onLogout = {}, onBack = {}) }
}

@Preview(showBackground = true, name = "Login Loading")
@Composable
fun LoginLoadingPreview() {
    KemsuTheme { LoginContent(user = "ivanov", pass = "12345", onUser = {}, onPass = {}, rememberMe = true, onRemember = {}, isLoading = true, error = "Неверный логин/пароль", onLogin = {}, onLogout = {}, onBack = {}) }
}
