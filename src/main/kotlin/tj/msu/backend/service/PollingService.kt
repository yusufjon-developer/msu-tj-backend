package tj.msu.backend.service

import org.slf4j.LoggerFactory
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant

data class CheckResult(
    val url: String,
    val lastModified: String?,
    val isChanged: Boolean
)

@Service
class PollingService(
    private val restClient: RestClient
) {
    private val logger = LoggerFactory.getLogger(PollingService::class.java)

    /**
     * Checks if the file at the given URL has been modified since [oldLastModified].
     * Uses HTTP HEAD request.
     */
    fun checkFileHeader(url: String, oldLastModified: String?): CheckResult {
        val start = Instant.now()
        
        return try {
            val response = restClient.method(HttpMethod.HEAD)
                .uri(url)
                .retrieve()
                .toBodilessEntity()

            val newLastModified = response.headers.getFirst("Last-Modified")
            val duration = java.time.Duration.between(start, Instant.now()).toMillis()

            if (response.statusCode.value() != 200) {
                 logger.warn("[HEAD] {} | Status: {} | Time: {}ms", url, response.statusCode, duration)
                 return CheckResult(url, oldLastModified, false)
            }

            if (oldLastModified == null && newLastModified != null) {
                logger.info("[HEAD] {} | Time: {}ms | Initial fetch -> Update required (Last-Modified: {})", url, duration, newLastModified)
                return CheckResult(url, newLastModified, true)
            }

            if (newLastModified != null && newLastModified != oldLastModified) {
                logger.info("[HEAD] {} | Time: {}ms | Update detected: {} -> {}", url, duration, oldLastModified, newLastModified)
                return CheckResult(url, newLastModified, true)
            }

            CheckResult(url, oldLastModified, false)

        } catch (e: Exception) {
            logger.error("[HEAD] Request error for {}: {}", url, e.message)
            CheckResult(url, oldLastModified, false)
        }
    }

    fun downloadFile(url: String): ByteArray {
        logger.info("Downloading file from {}", url)
        return restClient.get()
            .uri(url)
            .retrieve()
            .body(ByteArray::class.java)
            ?: throw IllegalStateException("Empty body received from $url")
    }
}
