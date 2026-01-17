package tj.msu.backend.scheduler

import com.google.cloud.firestore.Firestore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tj.msu.backend.model.ExamEvent
import tj.msu.backend.service.NotificationService
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class ExamScheduler(
    private val firestore: Firestore,
    private val notificationService: NotificationService
) {
    private val logger = LoggerFactory.getLogger(ExamScheduler::class.java)

    /**
     * Runs daily at 9:00 AM Dushanbe time.
     * Checks for exams scheduled for Tomorrow (1 day before) and Day After Tomorrow (2 days before).
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Dushanbe")
    fun checkExams() {
        logger.info("Checking for upcoming exams...")
        try {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val dayAfterTomorrow = today.plusDays(2)
            
            // Fetch all scheduled exams from Firestore
            // Optimization: Could query by date range, but string dates "YYYY-MM-DD" are sortable.
            // Let's filter in memory for simplicity unless volume is huge.
            val query = firestore.collection("scheduled_exams").get().get()
            val allExams = query.toObjects(ExamEvent::class.java)
            
            logger.info("Found {} total scheduled exams.", allExams.size)

            allExams.forEach { exam ->
                val examDate = LocalDate.parse(exam.date)
                
                var daysUntil = -1L
                if (examDate.isEqual(tomorrow)) daysUntil = 1
                else if (examDate.isEqual(dayAfterTomorrow)) daysUntil = 2
                
                if (daysUntil != -1L) {
                    sendExamNotification(exam, daysUntil)
                }
            }

        } catch (e: Exception) {
            logger.error("Error checking exams: {}", e.message)
        }
    }

    private fun sendExamNotification(exam: ExamEvent, daysUntil: Long) {
        val whenText = if (daysUntil == 1L) "Завтра" else "Послезавтра"
        
        val text = "$whenText состоится \"${exam.type}\" по предмету \"${exam.subject}\". Начало в ${exam.time}. Аудитория ${exam.room}."
        val title = "Напоминание об экзамене"

        logger.info("Sending exam notification for group {} ({} days before): {}", exam.group, daysUntil, exam.subject)
        
        notificationService.sendToGroupWithHistory(
            facultyCode = exam.faculty,
            course = exam.course,
            title = title,
            body = text
        )
    }
}
