package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.model.Book
import com.example.data.model.BorrowRecord
import com.example.data.model.Fine
import com.example.data.model.NotificationItem
import com.example.data.model.Reservation
import com.example.data.model.User

@Database(
    entities = [
        Book::class,
        User::class,
        BorrowRecord::class,
        Reservation::class,
        Fine::class,
        NotificationItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class LibEaseDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun userDao(): UserDao
    abstract fun borrowRecordDao(): BorrowRecordDao
    abstract fun reservationDao(): ReservationDao
    abstract fun fineDao(): FineDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        @Volatile
        private var INSTANCE: LibEaseDatabase? = null

        fun getDatabase(context: Context): LibEaseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LibEaseDatabase::class.java,
                    "libease_database"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
