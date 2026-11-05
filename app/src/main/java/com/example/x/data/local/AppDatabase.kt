package com.example.x.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CourseEntity::class, LabEntity::class, EventEntity::class, UserEntity::class, StudentInfoEntity::class, TaskCacheEntity::class, ScheduleCacheEntity::class, TeacherListCacheEntity::class, TeacherScheduleCacheEntity::class],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun labDao(): LabDao
    abstract fun userDao(): UserDao
    abstract fun eventDao(): EventDao
    abstract fun studentInfoDao(): StudentInfoDao
    abstract fun taskCacheDao(): TaskCacheDao
    abstract fun scheduleCacheDao(): ScheduleCacheDao
    abstract fun teacherListCacheDao(): TeacherListCacheDao
    abstract fun teacherScheduleCacheDao(): TeacherScheduleCacheDao

    companion object {
        const val DB_NAME = "kemsu.db"

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                    .fallbackToDestructiveMigration(true).build().also { INSTANCE = it }
            }
    }
}
