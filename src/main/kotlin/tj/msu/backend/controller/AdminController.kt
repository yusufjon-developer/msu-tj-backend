package tj.msu.backend.controller

import com.google.cloud.firestore.Firestore
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tj.msu.backend.service.AdminNotificationService

@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val firestore: Firestore,
    private val adminNotificationService: AdminNotificationService
) {

    data class TopicDTO(val id: String, val name: String, val description: String)
    
    data class UserDTO(
        val uid: String, 
        val email: String, 
        val name: String, 
        val facultyCode: String, 
        val course: Int, 
        val role: String
    )
    
    data class SendNotificationRequest(
        val title: String,
        val body: String,
        val targetType: String, // global, teachers, students, group, user
        val targetValue: String?, // required for group (pmi_1) or user (uid)
        val saveToHistory: Boolean = true
    )

    @GetMapping("/notifications/topics")
    fun getTopics(): ResponseEntity<List<TopicDTO>> {
        val topics = mutableListOf<TopicDTO>()
        
        // Static topics
        topics.add(TopicDTO("global", "Все пользователи", "Все студенты и преподаватели"))
        topics.add(TopicDTO("teachers", "Преподаватели", "Только преподаватели"))
        topics.add(TopicDTO("students", "Студенты", "Все студенты (и пользователи без роли)"))
        
        // Groups
        val faculties = listOf("pmi", "geo", "mo", "ling", "gmu", "hfmm")
        faculties.forEach { fac ->
            for (i in 1..4) {
                 topics.add(TopicDTO("${fac}_$i", "${fac.uppercase()} $i курс", "Группа $fac, $i курс"))
            }
        }
        
        return ResponseEntity.ok(topics)
    }

    @GetMapping("/users")
    fun getUsers(
        @RequestParam(required = false) email: String?,
        @RequestParam(defaultValue = "20") limit: Int
    ): ResponseEntity<List<UserDTO>> {
        // Simple listing with optional search
        val usersRef = firestore.collection("users")
        var query = usersRef.limit(limit)
        
        if (!email.isNullOrBlank()) {
            // Firestore doesn't support partial string match natively efficiently
            // But we can do exact matches or simple range if needed.
            // For now, let's assume exact match or just simple listing.
            // If user really wants search, we might need a dedicated search service or fetch-all.
            // Let's implement prefix search standard approach:
            query = usersRef.whereGreaterThanOrEqualTo("email", email)
                            .whereLessThanOrEqualTo("email", email + "\uf8ff")
                            .limit(limit)
        }

        val snapshot = query.get().get()
        val users = snapshot.documents.map { doc ->
            UserDTO(
                uid = doc.id,
                email = doc.getString("email") ?: "",
                name = doc.getString("name") ?: "Без имени",
                facultyCode = doc.getString("faculty_code") ?: "",
                course = doc.getLong("course")?.toInt() ?: 0,
                role = doc.getString("role") ?: "student"
            )
        }
        
        return ResponseEntity.ok(users)
    }

    @PostMapping("/notifications/send")
    fun sendNotification(@RequestBody request: SendNotificationRequest): ResponseEntity<Map<String, Any>> {
        if (request.title.isBlank() || request.body.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Title and body are required"))
        }

        adminNotificationService.sendNotification(
            request.title,
            request.body,
            request.targetType,
            request.targetValue,
            request.saveToHistory
        )
        
        return ResponseEntity.ok(mapOf("success" to true, "message" to "Notification queued"))
    }
}
