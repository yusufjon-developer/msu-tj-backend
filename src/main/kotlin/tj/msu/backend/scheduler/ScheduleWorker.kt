package tj.msu.backend.scheduler

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tj.msu.backend.config.PollingProperties
import tj.msu.backend.model.GroupSchedule
import tj.msu.backend.service.FirebaseService
import tj.msu.backend.service.PollingService
import tj.msu.backend.service.ScheduleProcessingService
import tj.msu.backend.service.XlsParserService
import java.util.concurrent.ConcurrentHashMap

@Component
class ScheduleWorker(
    private val pollingProperties: PollingProperties,
    private val pollingService: PollingService,
    private val xlsParserService: XlsParserService,
    private val processingService: ScheduleProcessingService,
    private val firebaseService: FirebaseService
) {
    private val logger = LoggerFactory.getLogger(ScheduleWorker::class.java)
    
    // Map to store last modified dates for each URL
    private val lastModifiedMap = ConcurrentHashMap<String, String>()

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        logger.info("Application ready. Triggering initial poll...")
        processUrls()
    }

    // Dushanbe Time Zone is UTC+5. 
    // Cron expression format: second, minute, hour, day of month, month, day of week
    
    // Active hours polling: Every 15 seconds between 06:00 and 19:00
    @Scheduled(cron = "0/15 * 6-18 * * *", zone = "Asia/Dushanbe")
    fun pollActiveHours() {
        // poll() // Commented out for now until functionality is fully verified, or we can run it.
        // For development, let's log.
        logger.debug("Active hours poll triggered")
        processUrls()
    }

    // Passive hours polling: Every 10 minutes between 19:00 and 06:00
    @Scheduled(cron = "0 */10 0-5,19-23 * * *", zone = "Asia/Dushanbe")
    fun pollPassiveHours() {
        logger.debug("Passive hours poll triggered")
        processUrls()
    }

    private fun processUrls() {
        var needUpdate = false
        
        pollingProperties.terminals.forEach { url ->
            val result = pollingService.checkFileHeader(url, lastModifiedMap[url])
            
            if (result.isChanged) {
                lastModifiedMap[url] = result.lastModified ?: ""
                needUpdate = true
            }
        }

        if (needUpdate) {
            logger.info("Changes detected. Starting database update process...")
            try {
                // Collect results from all files
                val globalData = ConcurrentHashMap<String, GroupSchedule>()
                var successCount = 0

                pollingProperties.terminals.forEach { url ->
                    try {
                        val fileBytes = pollingService.downloadFile(url)
                        // Note: parseXls modifies globalData in place
                        // We use a synchronized map or just a standard map if we are single threaded here.
                        // Since processUrls is called from @Scheduled which is single threaded by default (unless configured otherwise),
                        // and we use sequential forEach, a standard map would work, but ConcurrentHashMap is safer.
                        xlsParserService.parseXls(fileBytes, globalData)
                        successCount++
                    } catch (e: Exception) {
                        logger.error("Error processing {}: {}", url, e.message)
                    }
                }

                if (successCount > 0 && globalData.isNotEmpty()) {
                    // Processing
                    val freeRooms = processingService.calculateFreeRooms(globalData)
                    val teachers = processingService.extractTeachers(globalData)
                    
                    logger.info("Extracted {} teachers schedules", teachers.size)

                    // Firebase Sync
                    firebaseService.saveFullUpdate(globalData, freeRooms, teachers)
                } else {
                    logger.warn("No valid data received, skipping database update.")
                }

            } catch (e: Exception) {
               logger.error("Global update process failed: {}", e.message) 
            }
        }
    }
}
