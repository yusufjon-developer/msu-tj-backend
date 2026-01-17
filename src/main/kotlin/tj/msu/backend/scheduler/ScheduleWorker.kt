package tj.msu.backend.scheduler

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tj.msu.backend.config.PollingProperties
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
    private val firebaseService: FirebaseService,
    private val notificationService: tj.msu.backend.service.NotificationService
) {
    private val logger = LoggerFactory.getLogger(ScheduleWorker::class.java)
    

    private val lastModifiedMap = ConcurrentHashMap<String, String>()
    
    private var oldGlobalData: Map<String, tj.msu.backend.model.GroupSchedule> = emptyMap()
    private var isNextWeekPublished = false

    @EventListener(ApplicationReadyEvent::class)
    fun onStartup() {
        logger.info("Application ready. Fetching initial state...")
        oldGlobalData = firebaseService.fetchCurrentSchedule()
        val nextSched = firebaseService.fetchNextSchedule()
        isNextWeekPublished = nextSched.isNotEmpty()
        
        logger.info("State initialized. Current Groups: {}, Next Week published: {}", oldGlobalData.size, isNextWeekPublished)
        
        processUrls()
    }


    

    @Scheduled(cron = "0/15 * 6-18 * * *", zone = "Asia/Dushanbe")
    fun pollActiveHours() {
        logger.debug("Active hours poll triggered")
        processUrls()
    }


    @Scheduled(cron = "0 */10 0-5,19-23 * * *", zone = "Asia/Dushanbe")
    fun pollPassiveHours() {
        logger.debug("Passive hours poll triggered")
        processUrls()
    }

    @Synchronized
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

                val result = XlsParserService.ParsingResult()
                var successCount = 0

                pollingProperties.terminals.forEach { url ->
                    try {
                        val fileBytes = pollingService.downloadFile(url)
                        xlsParserService.parseXls(fileBytes, result)
                        successCount++
                    } catch (e: Exception) {
                        logger.error("Error processing {}: {}", url, e.message)
                    }
                }
                
                val globalData = result.groups

                if (successCount > 0 && globalData.isNotEmpty()) {

                    val freeRooms = processingService.calculateFreeRooms(globalData)
                    val teachers = processingService.extractTeachers(globalData)
                    val isNextWeek = processingService.isNextWeek(result.dates)
                    
                    logger.info("Extracted {} teachers. Detected Week: {}. NextWeek={}", teachers.size, result.weekNumber, isNextWeek)

                    firebaseService.saveFullUpdate(globalData, freeRooms, teachers, result.weekNumber, result.dates)
                    
                    if (isNextWeek) {
                        if (!isNextWeekPublished) {
                            logger.info("New week schedule detected for the first time. Sending global notification.")
                            notificationService.sendToTopic(
                                "global", 
                                "Расписание на новую неделю", 
                                "Расписание на следующую неделю опубликовано. Спланируйте свое время заранее!"
                            )
                            isNextWeekPublished = true
                        }
                    } else {
                        // Current Week Update
                        if (isNextWeekPublished) {
                            isNextWeekPublished = false
                        }
                        
                        // Compare with DB state (loaded into oldGlobalData on startup)
                        if (oldGlobalData.isNotEmpty()) {
                             val diffs = processingService.findDifferences(oldGlobalData, globalData)
                             if (diffs.isNotEmpty()) {
                                 logger.info("Detected schedule changes (vs Database/Previous) in {} groups: {}", diffs.size, diffs)
                                 diffs.forEach { groupId ->
                                     notificationService.sendToTopic(
                                         groupId,
                                         "Обновление расписания",
                                         "В расписание вашей группы были внесены изменения. Пожалуйста, проверьте актуальное расписание."
                                     )
                                 }
                             } else {
                                 logger.info("No differences found between Schedule and Database state.")
                             }
                        } else {
                             logger.info("Database state was empty on startup. Skipping change notifications for initial sync.")
                        }

                        oldGlobalData = globalData
                    }
                    

                    if (result.dates.isNotEmpty()) {
                        val exams = processingService.extractExams(globalData, result.dates)
                        if (exams.isNotEmpty()) {
                            firebaseService.saveExams(exams)
                        }
                    }

                } else {
                    logger.warn("No valid data received, skipping database update.")
                }

            } catch (e: Exception) {
               logger.error("Global update process failed: {}", e.message) 
            }
        }
    }
}
