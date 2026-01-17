package tj.msu.backend.service

import org.springframework.stereotype.Service
import tj.msu.backend.model.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class ScheduleProcessingService {

    private val teacherNameRegex = Pattern.compile("([А-ЯЁ][а-яё]+[\\s\\xA0]+[А-ЯЁ]\\.[\\s\\xA0]*[А-ЯЁ]\\.?)")

    private val allRooms = listOf(
        "100", "101", "102", "103", "104", "105", "106", "107", "108",
        "208",
        "301", "302",
        "401", "402", "403", "404",
        "601", "602", "603",
        "701", "702", "703", "704",
        "801", "802",
        "лабГЕО", "лабФИЗ", "лабХИМ", "стд"
    )

    private val dayKeys = listOf("1", "2", "3", "4", "5", "6", "7")
    private val pairKeys = listOf("1", "2", "3", "4", "5")
    private val daysNames = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    fun calculateFreeRooms(groups: Map<String, GroupSchedule>): FreeRoomsData {
        val scheduleMap = HashMap<String, Map<String, List<String>>>()

        for (i in dayKeys.indices) {
            val dayKey = dayKeys[i]
            val pairsMap = HashMap<String, List<String>>()

            for (j in pairKeys.indices) {
                val pairKey = pairKeys[j]
                val occupiedSet = HashSet<String>()

                for (group in groups.values) {
                    if (i < group.days.size) {
                        val daySched = group.days[i]
                        val lessons = daySched.lessons
                        if (lessons != null && j < lessons.size) {
                            val lesson = lessons[j]
                            lesson?.rooms?.forEach { room ->
                                occupiedSet.add(room)
                            }
                        }
                    }
                }

                val freeRooms = allRooms.filter { !occupiedSet.contains(it) }
                pairsMap[pairKey] = freeRooms
            }
            scheduleMap[dayKey] = pairsMap
        }

        return FreeRoomsData(
            schedule = scheduleMap,
            lastUpdate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        )
    }

    fun extractTeachers(groups: Map<String, GroupSchedule>): Map<String, TeacherSchedule> {
        val teachersMap = HashMap<String, TeacherSchedule>()

        groups.values.forEach { group ->
            group.days.forEachIndexed { dayIdx, day ->
                day.lessons?.forEachIndexed { lessonIdx, lesson ->
                    if (lesson != null) {
                        lesson.teacher.forEach { rawName ->
                            processTeacherName(rawName, teachersMap, group, lesson, dayIdx, lessonIdx)
                        }
                    }
                }
            }
        }


        val forbiddenChars = "$.#[]/"
        return teachersMap.filter { (name, _) ->
            name.isNotBlank() && name.none { forbiddenChars.contains(it) || it.code < 32 }
        }
    }

    private fun processTeacherName(
        rawName: String,
        teachersMap: MutableMap<String, TeacherSchedule>,
        group: GroupSchedule,
        lesson: Lesson,
        dayIdx: Int,
        lessonIdx: Int
    ) {
        if (rawName.isBlank()) return

        val matcher = teacherNameRegex.matcher(rawName)
        val matches = ArrayList<String>()
        while (matcher.find()) {
            matches.add(matcher.group())
        }

        if (matches.isNotEmpty()) {
            matches.forEach { name ->
                val safeName = sanitizeName(name)
                if (safeName.isNotBlank()) {
                    addLessonToTeacher(teachersMap, safeName, group, lesson, dayIdx, lessonIdx)
                }
            }
        } else {

            val junkWords = listOf(
                "английский", "немецкий", "китайский", "французский",
                "язык", "группа", "подгруппа", "физ", "пр.", "лк.", "[пз]", "(", ")"
            )
            var cleaned = rawName
            junkWords.forEach { junk ->
                cleaned = cleaned.replace(junk, "", ignoreCase = true)
            }



            val safeName = sanitizeName(cleaned)
            if (safeName.trim() != "Иностранный" && safeName.length >= 3) {
                 addLessonToTeacher(teachersMap, safeName, group, lesson, dayIdx, lessonIdx)
            }
        }
    }

    private fun addLessonToTeacher(
        teachersMap: MutableMap<String, TeacherSchedule>,
        name: String,
        group: GroupSchedule,
        lesson: Lesson,
        dayIdx: Int,
        lessonIdx: Int
    ) {
        val schedule = teachersMap.computeIfAbsent(name) {
             TeacherSchedule(
                 name = it,
                 updatedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                 days = MutableList(7) { i -> DaySchedule(day = daysNames[i]) }
             )
        }


        if (schedule.days[dayIdx].lessons == null) {
            schedule.days[dayIdx].lessons = MutableList(7) { null } // Max pairs? Usually 5-7
        }
        val lessons = schedule.days[dayIdx].lessons!!


        while (lessons.size <= lessonIdx) {
            lessons.add(null)
        }

        val existingLesson = lessons[lessonIdx]

        if (existingLesson != null) {
            val alreadyAdded = existingLesson.teacher.contains(group.title)
            if (!alreadyAdded) {

                existingLesson.teacher = existingLesson.teacher + group.title
            }
        } else {
            val newLesson = Lesson(
                subject = lesson.subject,
                type = lesson.type,
                rooms = lesson.rooms,
                teacher = listOf(group.title) // In teacher schedule, 'teacher' field holds group names
            )
            lessons[lessonIdx] = newLesson
        }
    }

    private fun sanitizeName(name: String): String {
        var str = name.replace("\u0000", "").trim()
        val idx = str.indexOf("(")


        str = str.replace(".", "_")
            .replace("/", "-")
            .replace("#", "")
            .replace("$", "")
            .replace("[", "(")
            .replace("]", ")")
            .replace("\n", "")
            .replace("\r", "")
            .replace("\t", "")
            .trim(':',' ', ',')

        return str
    }


    fun findDifferences(oldGroups: Map<String, GroupSchedule>, newGroups: Map<String, GroupSchedule>): Set<String> {
        val changedGroups = HashSet<String>()
        newGroups.forEach { (name, newGroup) ->
            val oldGroup = oldGroups[name]
            if (oldGroup != null && !areLessonsEqual(oldGroup, newGroup)) {
                changedGroups.add(name)
            }
        }
        return changedGroups
    }

    private fun areLessonsEqual(g1: GroupSchedule, g2: GroupSchedule): Boolean {
        if (g1.days.size != g2.days.size) return false
        return g1.days.zip(g2.days).all { (d1, d2) ->
            d1.lessons == d2.lessons
        }
    }

    fun extractExams(groups: Map<String, GroupSchedule>, detectedDates: List<String>): List<ExamEvent> {
        val exams = ArrayList<ExamEvent>()

        groups.forEach { (groupId, group) ->
            val parts = groupId.split("_")
            if (parts.size >= 2) {
                val faculty = parts[0]
                val course = parts[1].toIntOrNull() ?: 0

                group.days.forEachIndexed { dayIdx, day ->
                    val date = if (dayIdx < detectedDates.size) detectedDates[dayIdx] else ""
                    if (date.isNotBlank()) {
                        day.lessons?.forEachIndexed { lessonIdx, lesson ->
                            if (lesson != null && isExamOrTest(lesson.type)) {
                                exams.add(ExamEvent(
                                    id = "${groupId}_${date}_${lessonIdx}",
                                    group = group.title,
                                    subject = lesson.subject,
                                    type = lesson.type,
                                    date = date,
                                    time = getPairStartTime(lessonIdx),
                                    room = lesson.rooms.joinToString(", "),
                                    faculty = faculty,
                                    course = course
                                ))
                            }
                        }
                    }
                }
            }
        }
        return exams
    }

    private fun isExamOrTest(type: String): Boolean {
        val t = type.lowercase()
        return t.contains("экзамен") || t.contains("зачет")
    }

    private fun getPairStartTime(index: Int): String {
        return when(index) {
            0 -> "08:00"
            1 -> "09:35"
            2 -> "11:10"
            3 -> "13:00"
            4 -> "14:35" // Approx
            else -> "??:??"
        }
    }

    fun isNextWeek(detectedDates: List<String>): Boolean {
        if (detectedDates.isEmpty()) return false
        val now = java.time.LocalDate.now()
        val minDateStr = detectedDates.minOrNull() ?: return false
        val minDate = java.time.LocalDate.parse(minDateStr)
        val fileWeek = minDate.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
        val currentWeek = now.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())

        return fileWeek > currentWeek || (fileWeek < 5 && currentWeek > 50)
    }
}
