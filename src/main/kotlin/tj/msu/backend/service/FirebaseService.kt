package tj.msu.backend.service

import com.google.firebase.database.FirebaseDatabase
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tj.msu.backend.model.FreeRoomsData
import tj.msu.backend.model.GroupSchedule
import tj.msu.backend.model.TeacherSchedule
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CompletableFuture

@Service
class FirebaseService(
    private val firebaseDatabase: FirebaseDatabase
) {
    private val logger = LoggerFactory.getLogger(FirebaseService::class.java)

    fun saveFullUpdate(
        groups: Map<String, GroupSchedule>,
        freeRooms: FreeRoomsData,
        teachers: Map<String, TeacherSchedule>
    ) {

        try {
            // We can use updateChildren for atomic update or individual set calls
            // Go code used: NewRef("schedules").Set(...), NewRef("free_rooms").Set(...)
            // Let's do roughly the same using async tasks

            val f1 = setAsync("schedules", groups)
            val f2 = setAsync("free_rooms", freeRooms)
            val f3 = setAsync("teachers", teachers)
            
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            val f4 = setAsync("last_global_update", timestamp)

            CompletableFuture.allOf(f1, f2, f3, f4).join()
            logger.info("Data (Groups, FreeRooms, Teachers) successfully sent to Firebase")

        } catch (e: Exception) {
            logger.error("Failed to save to Firebase: {}", e.message)
            throw e
        }
    }

    private fun setAsync(path: String, value: Any): CompletableFuture<Void> {
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
