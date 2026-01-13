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
    // 5 pairs per day, nullable if empty
    var lessons: MutableList<Lesson?> = MutableList(5) { null }
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
