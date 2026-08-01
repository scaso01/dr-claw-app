package com.scaso.drclawapp.data.local

import androidx.room.TypeConverter
import com.scaso.drclawapp.data.model.MessageType
import com.scaso.drclawapp.data.model.Role

class Converters {

    @TypeConverter
    fun fromRole(role: Role): String = role.name

    @TypeConverter
    fun toRole(value: String): Role = Role.valueOf(value)

    @TypeConverter
    fun fromMessageType(type: MessageType): String = type.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)
}
