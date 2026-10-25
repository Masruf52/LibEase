package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.NotificationItem
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun getAllNotifications(): Flow<List<NotificationItem>>

    @Query("SELECT * FROM notifications WHERE userId = :userId OR userId = 'ALL' ORDER BY createdAt DESC")
    fun getNotificationsForUser(userId: String): Flow<List<NotificationItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(item: NotificationItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(items: List<NotificationItem>)

    @Update
    suspend fun updateNotification(item: NotificationItem)

    @Query("UPDATE notifications SET isRead = 1 WHERE userId = :userId OR userId = 'ALL'")
    suspend fun markAllAsRead(userId: String)

    @Delete
    suspend fun deleteNotification(item: NotificationItem)
}
