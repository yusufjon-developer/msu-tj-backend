package tj.msu.backend.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.polling")
class PollingProperties(
    var terminals: List<String> = listOf(
        "https://msu.tj/file/timetable/enf.xls",
        "https://msu.tj/file/timetable/gf.xls"
    )
)
