package tj.msu.backend.model

data class Lesson(
    var subject: String = "",
    var type: String = "",
    var teacher: List<String> = emptyList(),
    var rooms: List<String> = emptyList()
)

data class DaySchedule(
    var day: String = "",
    var date: String? = null,
    // 5 pairs per day, nullable list if day is free, nullable items if slot is free
    var lessons: MutableList<Lesson?>? = null
)

data class GroupSchedule(
    var id: String = "",
    var title: String = "",
    var updatedAt: String = "",
    // 7 days (Mon-Sun)
    var days: MutableList<DaySchedule> = ArrayList()
)

data class TeacherSchedule(
    var name: String = "",
    var updatedAt: String = "",
    var days: MutableList<DaySchedule> = ArrayList()
)

data class FreeRoomsData(
    // Map<Day, Map<Pair, List<Room>>>
    var schedule: Map<String, Map<String, List<String>>> = emptyMap(),
    var lastUpdate: String = ""
)

data class ExamEvent(
    val id: String = "", // Unique ID (e.g. group_date_pair)
    val group: String = "",
    val subject: String = "",
    val type: String = "",
    val date: String = "", // YYYY-MM-DD
    val time: String = "", // HH:mm
    val room: String = "",
    val faculty: String = "",
    val course: Int = 0
)
