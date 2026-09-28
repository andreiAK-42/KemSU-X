package com.example.x.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseDao {
    @Query("SELECT * FROM courses WHERE studyYearFilter = :year ORDER BY num ASC")
    fun observeByYear(year: String): Flow<List<CourseEntity>>
    @Query("SELECT * FROM courses ORDER BY num ASC")
    fun observeAll(): Flow<List<CourseEntity>>
    @Query("SELECT * FROM courses WHERE discipline LIKE '%' || :query || '%' OR teacher LIKE '%' || :query || '%' ORDER BY num ASC")
    fun search(query: String): Flow<List<CourseEntity>>
    @Query("SELECT * FROM courses WHERE cId = :cId LIMIT 1")
    suspend fun getByCId(cId: String): CourseEntity?
    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?
    @Query("DELETE FROM courses WHERE studyYearFilter = :year")
    suspend fun deleteByYear(year: String)
    @Query("DELETE FROM courses")
    suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CourseEntity>)
}

@Dao
interface LabDao {
    @Query("SELECT * FROM labs ORDER BY deadline ASC")
    fun observeAll(): Flow<List<LabEntity>>
    @Query("SELECT * FROM labs ORDER BY deadline ASC")
    suspend fun getAll(): List<LabEntity>
    @Query("DELETE FROM labs")
    suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LabEntity>)
}

@Dao
interface TaskCacheDao {
    @Query("SELECT * FROM task_cache WHERE cId = :cId ORDER BY idx ASC")
    suspend fun getByCId(cId: String): List<TaskCacheEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TaskCacheEntity>)
    @Query("DELETE FROM task_cache WHERE cId = :cId")
    suspend fun deleteByCId(cId: String)
    @Query("DELETE FROM task_cache")
    suspend fun deleteAll()
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users LIMIT 1")
    fun observe(): Flow<UserEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: UserEntity)
    @Query("DELETE FROM users")
    suspend fun deleteAll()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY id ASC")
    fun observeAll(): Flow<List<EventEntity>>
    @Query("SELECT COUNT(*) FROM events WHERE isRead = 0")
    fun unreadCount(): Flow<Int>
    @Query("UPDATE events SET isRead = 1")
    suspend fun markAllRead()
    @Query("DELETE FROM events")
    suspend fun deleteAll()
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EventEntity>)
}

@Dao
interface StudentInfoDao {
    @Query("SELECT * FROM student_info WHERE pk = 0 LIMIT 1")
    fun observe(): Flow<StudentInfoEntity?>
    @Query("SELECT * FROM student_info WHERE pk = 0 LIMIT 1")
    suspend fun get(): StudentInfoEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: StudentInfoEntity)
}
