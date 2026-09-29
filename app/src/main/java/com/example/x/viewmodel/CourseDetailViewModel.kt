package com.example.x.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.x.data.local.AppDatabase
import com.example.x.data.model.Course
import com.example.x.data.model.CourseTask
import com.example.x.data.remote.KemsuApi
import com.example.x.data.repository.CourseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CourseDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = CourseRepository(AppDatabase.get(app), KemsuApi(app), app)
    private val _course = MutableStateFlow<Course?>(null)
    val course: StateFlow<Course?> = _course
    private val _tasks = MutableStateFlow<List<CourseTask>>(emptyList())
    val tasks: StateFlow<List<CourseTask>> = _tasks
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun loadByCId(cId: String) {
        viewModelScope.launch {
            _course.value = repo.getCourseByCId(cId)
            loadTasks(cId)
        }
    }

    /**Получение списка лаб из дисциплины**/
    fun loadTasks(cId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val res = repo.fetchTasks(cId)

            if (res.isSuccess) {
                _tasks.value = res.getOrNull() ?: emptyList()
            }

            else _error.value = res.exceptionOrNull()?.message
            _loading.value = false
        }
    }
    fun refresh(cId: String) = loadTasks(cId)
}
