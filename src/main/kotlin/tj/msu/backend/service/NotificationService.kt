package tj.msu.backend.service

import com.google.cloud.firestore.Firestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.CompletableFuture

@Service
class NotificationService(
    private val firebaseMessaging: FirebaseMessaging,
    private val firestore: Firestore
) {
    private val logger = LoggerFactory.getLogger(NotificationService::class.java)

    /**
     * Sends a notification to a specific FCM topic.
     * Use this for immediate alerts (Global, Teachers, Group schedule updates) where history is NOT required.
     */
    fun sendToTopic(topic: String, title: String, body: String) {
        try {
            val message = Message.builder()
                .setTopic(topic)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build()
                )
                .build()

            val response = firebaseMessaging.send(message)
            logger.info("Sent notification to topic '{}': ID={}", topic, response)
        } catch (e: Exception) {
            logger.error("Failed to send notification to topic '{}': {}", topic, e.message)
        }
    }

    /**
     * Helper DTO for localized usage or we can use a map.
     */
    data class NotificationItem(
        val id: String = "",
        val title: String,
        val body: String,
        val timestamp: Long = System.currentTimeMillis(),
        val type: String = "info",
        val isRead: Boolean = false
    )

    /**
     * Sends a notification to a group AND saves it to the history of every student in that group.
     * Used for Exams/Tests reminders.
     * Warning: Fan-out write operation.
     */
    fun sendToGroupWithHistory(facultyCode: String, course: Int, title: String, body: String) {
        val topic = "${facultyCode}_$course"
        
        sendToTopic(topic, title, body)
        saveHistoryToGroup(facultyCode, course, title, body)
    }

    private fun saveHistoryToGroup(facultyCode: String, course: Int, title: String, body: String) {
        CompletableFuture.runAsync {
            try {
                // Find users
                val querySnapshot = firestore.collection("users")
                    .whereEqualTo("facultyCode", facultyCode)
                    .whereEqualTo("course", course)
                    .get()
                    .get() 

                if (querySnapshot.isEmpty) {
                    logger.warn("No users found for group {}_{} to save history.", facultyCode, course)
                    return@runAsync
                }

                val uids = querySnapshot.documents.map { it.id }
                saveHistoryToUsersSync(uids, title, body)
                
                logger.info("Saved notification history for {} users in {}_{}", uids.size, facultyCode, course)

            } catch (e: Exception) {
                logger.error("Failed to save history for group {}_{}: {}", facultyCode, course, e.message)
            }
        }
    }

    /**
     * Generic method to save history for a list of UIDs.
     * Can be used by Admin Panel for any target audience (Global, Teachers, Users).
     */
    fun saveHistoryToUsers(uids: List<String>, title: String, body: String) {
        CompletableFuture.runAsync {
            saveHistoryToUsersSync(uids, title, body)
        }
    }

    private fun saveHistoryToUsersSync(uids: List<String>, title: String, body: String) {
        val chunkSize = 450
        uids.chunked(chunkSize).forEach { chunk ->
             try {
                val batch = firestore.batch()
                chunk.forEach { uid ->
                    val notifRef = firestore.collection("users").document(uid).collection("notifications").document()
                    val notificationData = mapOf(
                        "id" to notifRef.id,
                        "title" to title,
                        "body" to body,
                        "timestamp" to System.currentTimeMillis(),
                        "type" to "info",
                        "isRead" to false
                    )
                    batch.set(notifRef, notificationData)
                }
                batch.commit().get()
             } catch (e: Exception) {
                 logger.error("Failed to commit batch history: {}", e.message)
             }
        }
        logger.info("Processed history saving for {} users.", uids.size)
    }
}
