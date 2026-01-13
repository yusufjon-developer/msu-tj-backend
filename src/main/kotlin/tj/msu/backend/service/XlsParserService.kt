package tj.msu.backend.service

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tj.msu.backend.model.DaySchedule
import tj.msu.backend.model.GroupSchedule
import tj.msu.backend.model.Lesson
import java.io.ByteArrayInputStream
import java.util.regex.Pattern

@Service
class XlsParserService {
    private val logger = LoggerFactory.getLogger(XlsParserService::class.java)

    // Regexes matching Go implementation
    private val roomRegex = Pattern.compile("\\b\\d{3}\\b")
    private val courseRegex = Pattern.compile("(\\d+)\\s*КУРС", Pattern.CASE_INSENSITIVE)
    private val typeRegex = Pattern.compile("\\[(.*?)\\]")
    private val teacherRegex = Pattern.compile("\\((.*?)\\)")

    private val directions = mapOf(
        "ПРИКЛАДНАЯ" to "pmi",
        "ХИМИЯ" to "hfmm",
        "ГЕОЛОГИЯ" to "geo",
        "МЕЖДУНАРОДНЫЕ" to "mo",
        "ЛИНГВИСТИКА" to "ling",
        "ГОСУДАРСТВЕННОЕ" to "gmu"
    )

    private val romanToArabic = mapOf(
        "I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6,
        "1" to 1, "2" to 2, "3" to 3, "4" to 4, "5" to 5, "6" to 6
    )

    private val daysNames = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )

    fun parseXls(fileBytes: ByteArray, globalData: MutableMap<String, GroupSchedule>) {
        try {
            ByteArrayInputStream(fileBytes).use { fis ->
                // WorkbookFactory handles both HSSF (.xls) and XSSF (.xlsx)
                val workbook = WorkbookFactory.create(fis)

                for (sheetIdx in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIdx)
                    var currentGroupId: String? = null
                    
                    // Iterate rows
                    for (row in sheet) {
                        val fullRowTextBuilder = StringBuilder()
                        // Naively concatenate all cells to check for header
                        for (cell in row) {
                            fullRowTextBuilder.append(" ").append(getCellText(cell))
                        }
                        val fullRowText = fullRowTextBuilder.toString().trim().uppercase()

                        // Header detection logic from Go: Contains "КУРС" and NOT "ПРАКТИЧЕСКИЙ"
                        val isHeaderCandidate = fullRowText.contains("КУРС") && !fullRowText.contains("ПРАКТИЧЕСКИЙ")

                        if (isHeaderCandidate) {
                            var foundCode = ""
                            for ((key, code) in directions) {
                                if (fullRowText.contains(key)) {
                                    foundCode = code
                                    break
                                }
                            }

                            val courseMatcher = courseRegex.matcher(fullRowText)
                            if (foundCode.isNotEmpty() && courseMatcher.find()) {
                                val courseNum = courseMatcher.group(1)
                                currentGroupId = "${foundCode}_${courseNum}"
                                val title = formatTitle(foundCode, courseNum)

                                if (!globalData.containsKey(currentGroupId)) {
                                    // Initialize structure for new group
                                    val days = ArrayList<DaySchedule>()
                                    for (d in 0 until 7) {
                                        days.add(DaySchedule(day = daysNames[d], lessons = MutableList(5) { null }))
                                    }
                                    globalData[currentGroupId] = GroupSchedule(
                                        id = currentGroupId,
                                        title = title,
                                        days = days
                                    )
                                    logger.debug("Group parsed: {}", title)
                                }
                                continue
                            }
                        }

                        // Parsing schedule rows
                        if (currentGroupId != null) {
                            val firstCell = getCellText(row.getCell(0, org.apache.poi.ss.usermodel.Row.MissingCellPolicy.CREATE_NULL_AS_BLANK))
                                .trim().trim('.', ' ')
                            
                            val pairNum = romanToArabic[firstCell]

                            if (pairNum != null) {
                                // It is a pair row
                                val pairIndex = pairNum - 1 // 0-based index

                                for (dayIdx in 0 until 7) {
                                    val subjCol = dayIdx * 2 + 1
                                    val roomCol = dayIdx * 2 + 2

                                    if (roomCol < row.lastCellNum) {
                                        val subjText = getCellText(row.getCell(subjCol)).trim()
                                        val roomText = getCellText(row.getCell(roomCol)).trim()

                                        if (subjText.isNotEmpty()) {
                                            val lesson = parseLessonString(subjText, roomText)
                                            val group = globalData[currentGroupId]!!
                                            
                                            if (dayIdx < group.days.size) {
                                                if (pairIndex in 0..4) {
                                                    group.days[dayIdx].lessons[pairIndex] = lesson
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing XLS: {}", e.message)
            throw RuntimeException(e)
        }
    }

    private fun getCellText(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                // Check if it's really an integer
                if (cell.numericCellValue % 1 == 0.0) {
                    cell.numericCellValue.toInt().toString()
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            else -> ""
        }
    }

    private fun parseLessonString(subjRaw: String, roomRaw: String): Lesson {
        var processingSubj = subjRaw
        var type = ""
        val teacherList = ArrayList<String>()

        // Extract Type
        val typeMatcher = typeRegex.matcher(processingSubj)
        if (typeMatcher.find()) {
            type = cleanType(typeMatcher.group(1))
            processingSubj = processingSubj.replace(typeMatcher.group(0), "", ignoreCase = true)
        }

        // Extract Teacher
        val teacherMatcher = teacherRegex.matcher(processingSubj)
        if (teacherMatcher.find()) {
            val teachersStr = teacherMatcher.group(1)
            teachersStr.split(",").forEach { 
                teacherList.add(it.trim())
            }
            processingSubj = processingSubj.replace(teacherMatcher.group(0), "", ignoreCase = true)
        }

        val subject = cleanSubject(processingSubj)
        val rooms = parseRooms(roomRaw)

        return Lesson(
            subject = subject,
            type = type,
            teacher = teacherList,
            rooms = rooms
        )
    }

    private fun parseRooms(text: String): List<String> {
        val rooms = ArrayList<String>()
        val matcher = roomRegex.matcher(text)
        while (matcher.find()) {
            rooms.add(matcher.group())
        }
        
        val lower = text.lowercase()
        if (lower.contains("физ")) rooms.add("лабФИЗ")
        if (lower.contains("хим")) rooms.add("лабХИМ")
        if (lower.contains("гео")) rooms.add("лабГЕО")
        if (lower.contains("стд")) rooms.add("стд")

        return rooms.map { it.removeSuffix(".0") }.distinct()
    }

    private fun cleanType(t: String): String {
        val up = t.uppercase()
        return when {
            up.contains("ЛК") -> "Лекция"
            up.contains("ПЗ") -> "Практика"
            up.contains("СЕМИНАР") -> "Семинар"
            up.contains("ЗАЧЕТ") -> "Зачет"
            up.contains("ЭКЗАМЕН") -> "Экзамен"
            else -> t
        }
    }

    private fun cleanSubject(s: String): String {
        return s.trim().replace("\\s+".toRegex(), " ")
    }

    private fun formatTitle(code: String, course: String): String {
        val base = when (code) {
            "pmi" -> "ПМИ"
            "hfmm" -> "ХФММ"
            "geo" -> "Геология"
            "mo" -> "МО"
            "ling" -> "Лингвистика"
            "gmu" -> "ГМУ"
            else -> code
        }
        return "$base, $course курс"
    }
}
