package tj.msu.backend.service

import com.google.cloud.firestore.Firestore
import com.google.firebase.database.FirebaseDatabase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tj.msu.backend.model.FreeRoomsData
import tj.msu.backend.model.GroupSchedule
import tj.msu.backend.model.TeacherSchedule
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class FirebaseService(
    private val firebaseDatabase: FirebaseDatabase,
    private val firestore: Firestore
) {
    private val logger = LoggerFactory.getLogger(FirebaseService::class.java)

    fun saveFullUpdate(
        groups: Map<String, GroupSchedule>,
        freeRooms: FreeRoomsData,
        teachers: Map<String, TeacherSchedule>,
        detectedWeek: Int?,
        detectedDates: List<String>
    ) {

        try {

            val now = java.time.LocalDate.now()
            val weekFields = WeekFields.of(Locale.getDefault())
            

            var isNextWeek = false
            
            if (detectedDates.isNotEmpty()) {

                val minDateStr = detectedDates.minOrNull()
                if (minDateStr != null) {
                    val minDate = java.time.LocalDate.parse(minDateStr) // Format YYYY-MM-DD
                    

                    val fileWeek = minDate.get(WeekFields.ISO.weekOfWeekBasedYear())
                    val currentWeek = now.get(WeekFields.ISO.weekOfWeekBasedYear())
                    
                    if (fileWeek > currentWeek || (fileWeek < 5 && currentWeek > 50)) { // Handling Year Wrap
                        isNextWeek = true
                    }
                    logger.info("Date Check: FileMin={}, FileWeek={}, CurrentWeek={}, IsNext={}", minDate, fileWeek, currentWeek, isNextWeek)
                }
            } else if (detectedWeek != null) {

            }


            var targetSuffix = "" 
            if (isNextWeek) {
                 targetSuffix = "_next"
            }
            
            logger.info("Syncing data to target: 'schedules{}'", targetSuffix)

            val f1 = setAsync("schedules$targetSuffix", groups)
            val f2 = setAsync("free_rooms$targetSuffix", freeRooms)
            val f3 = setAsync("teachers$targetSuffix", teachers)
            
            // CRITICAL USER REQ: "if week and current data are identical ... leave next empty"
            // So if target is Current, clear Next.
            var fClear: CompletableFuture<Void>? = null
            if (!isNextWeek) {


                val clearMap = null
                fClear = CompletableFuture.allOf(
                    setAsync("schedules_next", null),
                    setAsync("free_rooms_next", null),
                    setAsync("teachers_next", null)
                )
                logger.info("Cleared '_next' nodes as we are in Current week")
            }

            val f4 = setAsync("last_global_update", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
        

            val appInfo = mapOf(
                "latest_version" to "1.0.0",
                "changelog" to "Миграция на новый бэкенд (Kotlin v2). Добавлена история, даты и предпросмотр следующей недели.",
                "force_update" to false,
                "academic_week" to detectedWeek // User requested to save "exactly this" (academic week)
            )
            val f5 = setAsync("app_info", appInfo)


        if (detectedWeek != null) {
            archiveWeeklySchedule(groups, detectedWeek)
        } else {
            logger.warn("Skipping archiving: No academic week detected.")
        }
        
        val allFutures = mutableListOf(f1, f2, f3, f4, f5)
        if (fClear != null) allFutures.add(fClear)

        CompletableFuture.allOf(*allFutures.toTypedArray()).join()
            logger.info("Data (Groups, FreeRooms, Teachers, AppInfo) successfully sent to Firebase")

        } catch (e: Exception) {
            logger.error("Failed to save to Firebase: {}", e.message)
            throw e
        }
    }

    private fun archiveWeeklySchedule(groups: Map<String, GroupSchedule>, weekNumber: Int) {
        try {
            val now = LocalDateTime.now()
            
            val currentYear = now.year
            val currentMonth = now.monthValue
            
            // Academic Year: If >= Sept, Year-Year+1. Else Year-1-Year
            val academicYear = if (currentMonth >= 9) {
                "$currentYear-${currentYear + 1}"
            } else {
                "${currentYear - 1}-$currentYear"
            }
            
            val docId = weekNumber.toString()
            
            val archiveData = mapOf(
                "created_at" to now.toString(),
                "academic_year" to academicYear,
                "academic_week" to weekNumber,
                "data" to groups
            )
            
            // Structure: academic_years -> {Year} -> weeks -> {WeekNum}
            firestore.collection("academic_years")
                .document(academicYear)
                .collection("weeks")
                .document(docId)
                .set(archiveData)
                
            logger.info("Archived schedule to Firestore: Year={}, Week={}", academicYear, docId)
        } catch (e: Exception) {
            logger.error("Failed to archive schedule: {}", e.message)
        }
    }

    fun fetchCurrentSchedule(): Map<String, GroupSchedule> {
        val future = CompletableFuture<Map<String, GroupSchedule>>()
        firebaseDatabase.getReference("schedules").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val t = object : com.google.firebase.database.GenericTypeIndicator<Map<String, GroupSchedule>>() {}
                val data = snapshot.getValue(t) ?: emptyMap()
                future.complete(data)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                logger.error("Failed to fetch current schedule: {}", error.message)
                future.complete(emptyMap())
            }
        })
        return try {
            future.join()
        } catch (e: Exception) {
            logger.error("Error waiting for schedule fetch: {}", e.message)
            emptyMap()
        }
    }

    fun fetchNextSchedule(): Map<String, GroupSchedule> {
        val future = CompletableFuture<Map<String, GroupSchedule>>()
        firebaseDatabase.getReference("schedules_next").addListenerForSingleValueEvent(object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                val t = object : com.google.firebase.database.GenericTypeIndicator<Map<String, GroupSchedule>>() {}
                val data = snapshot.getValue(t) ?: emptyMap()
                future.complete(data)
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                logger.error("Failed to fetch next schedule: {}", error.message)
                future.complete(emptyMap())
            }
        })
        return try {
            future.join()
        } catch (e: Exception) {
            logger.error("Error waiting for next schedule fetch: {}", e.message)
            emptyMap()
        }
    }

    fun saveExams(exams: List<tj.msu.backend.model.ExamEvent>) {
        if (exams.isEmpty()) return
        
        try {

            val batch = firestore.batch()
            var count = 0
            exams.forEach { exam ->
                 // Collection: scheduled_exams
                 // Doc ID: exam.id (groupId_date_lessonIdx)
                 val docRef = firestore.collection("scheduled_exams").document(exam.id)
                 batch.set(docRef, exam)
                 count++
                 if (count >= 450) {
                     batch.commit().get()
                     count = 0
                 }
            }
            if (count > 0) batch.commit().get()
            logger.info("Saved {} exams to Firestore", exams.size)
        } catch (e: Exception) {
            logger.error("Failed to save exams: {}", e.message)
        }
    }

    private fun setAsync(path: String, value: Any?): CompletableFuture<Void> {
        val future = CompletableFuture<Void>()
        firebaseDatabase.getReference(path).setValue(value) { error, _ ->
            if (error != null) {
                future.completeExceptionally(error.toException())
            } else {
                future.complete(null)
            }
        }
        return future
    }
}
