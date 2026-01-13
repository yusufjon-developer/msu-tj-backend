package tj.msu.backend.service

import org.springframework.stereotype.Service
import tj.msu.backend.model.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

@Service
class ScheduleProcessingService {

    // Same regex as in teachers.go
    // ([А-ЯЁ][а-яё]+[\s\xA0]+[А-ЯЁ]\.[\s\xA0]*[А-ЯЁ]\.?)
    private val teacherNameRegex = Pattern.compile("([А-ЯЁ][а-яё]+[\\s\\xA0]+[А-ЯЁ]\\.[\\s\\xA0]*[А-ЯЁ]\\.?)")

    private val allRooms = listOf(
        "100", "101", "102", "103", "104", "105", "106", "107", "108",
        "208",
        "301", "302",
        "401", "402", "403", "404",
        "601", "602", "603",
        "701", "702", "703", "704",
        "801", "802",
        "лабГЕО", "лабФИЗ", "лабХИМ", "стд" // Note: Go code had лабГЕОЛ, but parser uses лабГЕО. Using parser's version to match.
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


        // forbiddenChars := ".$#[]/"
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
            
            // Go code had specific "Английский" removal again? 
            // strings.ReplaceAll(cleanedName, "Английский", "") was inside loop for "английский" junk.
            // Kotlin's replace(ignoreCase=true) covers it.

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
        if (idx != -1) {
            // Go code: if idx != -1 { } empty block?? 
            // See: name = strings.TrimSpace(name); if idx := strings.Index(name, "("); idx != -1 {} 
            // It seems the Go code did NOTHING with the index? 
            // "if idx := strings.Index(name, "("); idx != -1 {}" -> This loop body is empty in the file view I got. 
            // Step 137 lines 150-151.
            // Ah, looks like a bug or incomplete logic in legacy code? Or maybe I misread.
            // "if idx := strings.Index(name, "("); idx != -1 {}" 
            // Wait, looking at line 150: "if idx := strings.Index(name, "("); idx != -1 {"
            // Line 151: "}"
            // It really does nothing. I will faithfully REPLICATE the "doing nothing" or assumes it was intended to trim?
            // Actually, usually you cut off after (. 
            // But if legacy code does nothing, I should do nothing to maintain behavior parity unless it's obviously broken.
            // I'll skip "cutting off string" logic since Go code had empty block.
        }

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
}
