package tj.msu.backend.service

import com.google.cloud.firestore.Firestore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class AdminNotificationService(
    private val firestore: Firestore,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(AdminNotificationService::class.java)

    /**
     * Resolves the audience and sends notifications.
     */
    fun sendNotification(
        title: String,
        body: String,
        targetType: String,
        targetValue: String?,
        saveToHistory: Boolean
    ) {
        logger.info("Admin sending notification. Type={}, Value={}, Hist={}", targetType, targetValue, saveToHistory)
        
        // 1. Determine Topic or Token
        var topic: String? = null
        // For individual user, we might not have a reliable token here easily unless stored. 
        // Admin SDK might need tokens. 
        // IF targeting USER, we should rely on history saving mainly? No, user expects Push.
        // Assuming Tokens are not managed by Topic? 
        // For 'user' target, usually we send via Token. DB needs to store FCM tokens.
        // If we don't have tokens, we can only save to History.
        
        when (targetType) {
            "global" -> topic = "global"
            "teachers" -> topic = "teachers"
            "students" -> topic = "students" // New topic for all students
            "group" -> topic = targetValue // e.g. pmi_1
            "user" -> {
                // Individual user. Do we have their token?
                // If not, we can only save to history.
                logger.warn("Individual user push not fully supported without token storage. Saving to history only.")
            }
        }

        // 2. Send Push if Topic exists
        if (topic != null) {
            notificationService.sendToTopic(topic, title, body)
        }

        // 3. Save to History (Fan-out)
        if (saveToHistory) {
            CompletableFuture.runAsync {
                val uids = resolveAudience(targetType, targetValue)
                if (uids.isNotEmpty()) {
                    notificationService.saveHistoryToUsers(uids, title, body)
                } else {
                    logger.warn("No users found for targetType={} value={}", targetType, targetValue)
                }
            }
        }
    }

    private fun resolveAudience(targetType: String, targetValue: String?): List<String> {
        return try {
            val usersRef = firestore.collection("users")
            val query = when (targetType) {
                "global" -> usersRef // All users
                "teachers" -> usersRef.whereEqualTo("role", "teacher")
                "students" -> {
                     // "role" == "student" OR "role" is null/missing.
                     // Firestore OR queries are restricted. 
                     // Since we can't easily do (role == 'student' OR role == null), 
                     // We might fetch ALL and filter in memory, or just target 'student'.
                     // Pragmantic approach: User said "if not specified, it's also student".
                     // Fetch all, filter in code? Users list might be big?
                     // Optimization: "role" != "teacher" ? Firestore supports not equal?
                     // whereNotEqualTo("role", "teacher") is supported in modern SDKs.
                     usersRef.whereNotEqualTo("role", "teacher")
                }
                "group" -> {
                    // targetValue = "pmi_1"
                    val parts = targetValue?.split("_")
                    if (parts != null && parts.size == 2) {
                        usersRef.whereEqualTo("facultyCode", parts[0])
                                .whereEqualTo("course", parts[1].toIntOrNull() ?: 0)
                    } else {
                        return emptyList()
                    }
                }
                "user" -> {
                    if (targetValue != null) {
                        // Check if user exists?
                        // Just return the UID as list
                         return listOf(targetValue)
                    }
                    return emptyList()
                }
                else -> return emptyList()
            }

            // Execute Query
            // Note: If "user" case returned, we don't query.
            if (query == usersRef && targetType == "user") return listOf(targetValue!!) 
            
            // Special handling for 'user' type above prevented entering here if it was just returning list.
            // But query is 'usersRef' for global.
            
            // For 'user', we returned already.
            
            val snapshot = query.get().get()
            return snapshot.documents.map { it.id }

        } catch (e: Exception) {
            logger.error("Failed to resolve audience: {}", e.message)
            emptyList()
        }
    }
}
