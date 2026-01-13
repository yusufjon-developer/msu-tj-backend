package tj.msu.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@SpringBootApplication
class MsuBackendApplication

fun main(args: Array<String>) {
    runApplication<MsuBackendApplication>(*args)
}
