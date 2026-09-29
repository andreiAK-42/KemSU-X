package com.example.x.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**Дисциплины**/
@Entity(tableName = "courses")
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val num: Int,
    val discipline: String,
    val report: String,
    val year: String,
    val hours: Int,
    val period: String,
    val teacher: String,
    val points: Int,
    val cId: String?,
    val studyYearFilter: String
)

/**Лабораторные работы**/
@Entity(tableName = "labs")
data class LabEntity(
    @PrimaryKey val id: String,
    val discipline: String,
    val title: String,
    val deadline: String,
    val status: String,
    val points: Int,
    val maxPoints: Int,
    val changed: Boolean = false
)

/** Кеш расписания: одна строка (pk=0), обновляется только по кнопке пользователя. */
@Entity(tableName = "schedule_cache")
data class ScheduleCacheEntity(
    @PrimaryKey val pk: Int = 0,
    val dayInfoJson: String,
    val scheduleJson: String,
    val updatedAt: Long
)
/** Кеш заданий по курсу: чтобы не ждать сеть при каждом заходе. TTL проверяется по updatedAt. */
@Entity(tableName = "task_cache", primaryKeys = ["cId", "idx"])
data class TaskCacheEntity(
    val cId: String,
    val idx: Int,
    val title: String,
    val requires: String,
    val comment: String,
    val controlDate: String,
    val maxBall: Int,
    val result: String,
    val status: String,
    val flag: String,
    val updatedAt: Long
)

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val discipline: String,
    val text: String,
    val date: String,
    val isRead: Boolean = false
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val group: String,
    val faculty: String,
    val avatarUrl: String?
)

@Entity(tableName = "student_info")
data class StudentInfoEntity(
    @PrimaryKey val pk: Int = 0,
    val name: String?
)
