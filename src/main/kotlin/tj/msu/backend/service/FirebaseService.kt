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
                    
                    // Logic: If minDate is AFTER the upcoming Sunday? 
                    // Or if minDate indicates a week start that is > current week start.
                    // Let's use Week-of-Year comparison for simplicity using ISO fields
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
            // This implies: If we are updating "Current" (isNextWeek=false), we should INVALIDATE "Next".
            // Why? Because if we moved from Week 20 to 21, Week 21 becomes Current. The "Next" (Week 22) might not exist yet.
            // So if target is Current, clear Next.
            var fClear: CompletableFuture<Void>? = null
            if (!isNextWeek) {


                val clearMap = null // Setting value to null deletes it in Firebase? Yes.
                // Wait, setValue(null) deletes.
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


        archiveWeeklySchedule(groups)
        
        val allFutures = mutableListOf(f1, f2, f3, f4, f5)
        if (fClear != null) allFutures.add(fClear)

        CompletableFuture.allOf(*allFutures.toTypedArray()).join()
            logger.info("Data (Groups, FreeRooms, Teachers, AppInfo) successfully sent to Firebase")

        } catch (e: Exception) {
            logger.error("Failed to save to Firebase: {}", e.message)
            throw e
        }
    }

    private fun archiveWeeklySchedule(groups: Map<String, GroupSchedule>) {
        try {
            val now = LocalDateTime.now()
            val weekFields = WeekFields.of(Locale.getDefault())
            val weekNum = now.get(weekFields.weekOfWeekBasedYear())
            val year = now.year
            

            val docId = String.format("%d-W%02d", year, weekNum)
            
            val archiveData = mapOf(
                "created_at" to now.toString(),
                "year" to year,
                "week" to weekNum,
                "data" to groups
            )
            

            firestore.collection("schedules_history").document(docId).set(archiveData)
            logger.info("Archived schedule to Firestore: {}", docId)
        } catch (e: Exception) {
            logger.error("Failed to archive schedule: {}", e.message)
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
