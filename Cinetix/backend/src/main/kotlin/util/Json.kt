package util

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Gson + adapter tanggal/jam (Gson tidak bisa membaca java.time secara bawaan). */
object Json {
    private val JAM = DateTimeFormatter.ofPattern("HH:mm")
    private val WAKTU = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(LocalDate::class.java, JsonSerializer<LocalDate> { v, _, _ -> JsonPrimitive(v.toString()) })
        .registerTypeAdapter(LocalDate::class.java, JsonDeserializer { j, _, _ -> LocalDate.parse(j.asString.trim()) })
        .registerTypeAdapter(LocalTime::class.java, JsonSerializer<LocalTime> { v, _, _ -> JsonPrimitive(v.format(JAM)) })
        .registerTypeAdapter(LocalTime::class.java, JsonDeserializer { j, _, _ -> LocalTime.parse(j.asString.trim()) })
        .registerTypeAdapter(LocalDateTime::class.java, JsonSerializer<LocalDateTime> { v, _, _ -> JsonPrimitive(v.format(WAKTU)) })
        .registerTypeAdapter(LocalDateTime::class.java, JsonDeserializer { j, _, _ -> LocalDateTime.parse(j.asString.trim(), WAKTU) })
        .create()
}
