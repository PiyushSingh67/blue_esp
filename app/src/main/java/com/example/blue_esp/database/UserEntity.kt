package com.example.blue_esp.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "users")
@Serializable
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val email: String,
    val age: Int,
    val gender: String,
    val bloodGroup: String,
    val profilePictureUri: String? = null,
    val lastSync: Long = System.currentTimeMillis()
)
