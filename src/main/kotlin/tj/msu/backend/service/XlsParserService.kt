package tj.msu.backend.service

import org.apache.poi.ss.usermodel.*
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


    private val roomRegex = Pattern.compile("\\b\\d{3}\\b")
    private val courseRegex = Pattern.compile("(\\d+)\\s*КУРС", Pattern.CASE_INSENSITIVE)
    private val typeRegex = Pattern.compile("\\[(.*?)\\]")
    private val teacherRegex = Pattern.compile("\\((.*?)\\)")
    private val dateRegex = Pattern.compile("(\\d{1,2})\\s+([а-яА-Я]+)\\s+(\\d{4})")
    private val weekRegex = Pattern.compile("(\\d{1,2})\\s*[-]?\\s*я\\s+неделя", Pattern.CASE_INSENSITIVE)

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

    private val monthMap = mapOf(
        "января" to "01", "февраля" to "02", "марта" to "03", "апреля" to "04",
        "мая" to "05", "июня" to "06", "июля" to "07", "августа" to "08",
        "сентября" to "09", "октября" to "10", "ноября" to "11", "декабря" to "12"
    )

    data class ParsingResult(
        val groups: MutableMap<String, GroupSchedule> = HashMap(),
        var weekNumber: Int? = null,
        val dates: MutableList<String> = ArrayList()
    )

    fun parseXls(fileBytes: ByteArray, result: ParsingResult) {
        try {
            ByteArrayInputStream(fileBytes).use { fis ->
                val workbook = WorkbookFactory.create(fis)
                for (sheetIdx in 0 until workbook.numberOfSheets) {
                    val sheet = workbook.getSheetAt(sheetIdx)
                    parseSheet(sheet, result)
                }
            }
        } catch (e: Exception) {
            logger.error("Error parsing XLS: {}", e.message)
        }
    }

    private fun parseSheet(sheet: Sheet, result: ParsingResult) {
        val globalData = result.groups
        var currentGroup: GroupSchedule? = null
        var colToDayIndex = HashMap<Int, Int>() 
        

        if (result.weekNumber == null) {
            for (i in 0..5) {
                val row = sheet.getRow(i) ?: continue
                val sb = StringBuilder()
                for (cell in row) sb.append(getCellText(cell)).append(" ")
                val text = sb.toString()
                val m = weekRegex.matcher(text)
                if (m.find()) {
                    try { 
                        result.weekNumber = m.group(1).toInt() 
                        break 
                    } catch (e: Exception) {}
                }
            }
        }
        
        var r = 0
        while (r <= sheet.lastRowNum) {
            val row = sheet.getRow(r)
            if (row == null) { r++; continue }

            val fullRowTextBuilder = StringBuilder()
            for (c in 0 until row.lastCellNum) {
                fullRowTextBuilder.append(" ").append(getCellText(row.getCell(c)))
            }
            val fullRowText = fullRowTextBuilder.toString().trim().uppercase()

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
                    val groupId = "${foundCode}_${courseNum}"
                    val title = formatTitle(foundCode, courseNum)

                    if (!globalData.containsKey(groupId)) {
                        val days = ArrayList<DaySchedule>()
                        for (d in 0 until 7) {
                            days.add(DaySchedule(day = daysNames[d], lessons = MutableList(5) { null }))
                        }
                        val newGroup = GroupSchedule(id = groupId, title = title, days = days)
                        globalData[groupId] = newGroup
                        currentGroup = newGroup
                    } else {
                         currentGroup = globalData[groupId]
                    }

                    colToDayIndex.clear()
                    val dayRowIdx = r + 1
                    val dayRow = sheet.getRow(dayRowIdx)
                    if (dayRow != null) {
                       for (c in 0 until dayRow.lastCellNum) {
                           val text = getCellText(dayRow.getCell(c)).lowercase()
                           val dIdx = getDayIndex(text)
                           if (dIdx != -1) {
                               colToDayIndex[c] = dIdx
                               colToDayIndex[c+1] = dIdx
                           }
                       }
                    }

                    val dateRowIdx = r + 2
                    val dateRow = sheet.getRow(dateRowIdx)
                    if (dateRow != null && currentGroup != null) {
                        for (c in 0 until dateRow.lastCellNum) {
                             val text = getCellText(dateRow.getCell(c))
                             val parsedDate = parseRussianDate(text)
                             if (parsedDate != null) {
                                 result.dates.add(parsedDate)
                                 
                                 var dIdx = colToDayIndex[c]
                                 if (dIdx == null && c > 0) dIdx = colToDayIndex[c-1]
                                 
                                 if (dIdx != null && dIdx < currentGroup.days.size) {
                                     currentGroup.days[dIdx].date = parsedDate
                                 }
                             }
                        }
                    }
                    r += 2 
                }
            } else if (currentGroup != null) {
                val firstCell = getCellText(row.getCell(0)).trim().trim('.', ' ')
                val pairNum = romanToArabic[firstCell]

                if (pairNum != null) {
                    val pairIndex = pairNum - 1 
                    for (c in 0 until row.lastCellNum) {
                        if (!colToDayIndex.containsKey(c)) continue
                        val text = getCellText(row.getCell(c)).trim()
                        if (text.isEmpty()) continue
                        if (roomRegex.matcher(text).matches()) continue 
                        
                        val dayIdx = colToDayIndex[c] ?: continue
                        val nextCellText = getCellText(row.getCell(c+1)).trim()
                        val roomText = if (roomRegex.matcher(nextCellText).find() || nextCellText.lowercase().contains("лаб")) nextCellText else ""
                        val lesson = parseLessonString(text, roomText)
                        
                        if (dayIdx < currentGroup.days.size) {
                             val daySchedule = currentGroup.days[dayIdx]
                             val lessons = daySchedule.lessons!!
                             while (lessons.size <= pairIndex) lessons.add(null)
                             if (pairIndex >= 0 && pairIndex < 10) lessons[pairIndex] = lesson
                        }
                    }
                }
            }
            r++
        }
    }

    private fun getDayIndex(text: String): Int {
        return when {
            text.contains("понедельник") -> 0
            text.contains("вторник") -> 1
            text.contains("среда") -> 2
            text.contains("четверг") -> 3
            text.contains("пятница") -> 4
            text.contains("суббота") -> 5
            text.contains("воскресенье") -> 6
            else -> -1
        }
    }

    private fun parseRussianDate(text: String): String? {
        val matcher = dateRegex.matcher(text)
        if (matcher.find()) {
            val day = matcher.group(1).padStart(2, '0')
            val monthName = matcher.group(2).lowercase()
            val year = matcher.group(3)
            val month = monthMap[monthName] ?: return null
            return "$year-$month-$day"
        }
        return null
    }

    private fun getCellText(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toString()
                } else if (cell.numericCellValue % 1 == 0.0) {
                    cell.numericCellValue.toInt().toString()
                } else {
                    cell.numericCellValue.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            CellType.FORMULA -> {
                try { cell.stringCellValue } catch (e: Exception) { 
                   try { cell.numericCellValue.toString() } catch(e: Exception) { "" }
                }
            }
            else -> ""
        }
    }

    private fun parseLessonString(subjRaw: String, roomRaw: String): Lesson {
        var processingSubj = subjRaw
        var type = ""
        val teacherList = ArrayList<String>()

        val typeMatcher = typeRegex.matcher(processingSubj)
        if (typeMatcher.find()) {
            type = cleanType(typeMatcher.group(1))
            processingSubj = processingSubj.replace(typeMatcher.group(0), "", ignoreCase = true)
        }

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
