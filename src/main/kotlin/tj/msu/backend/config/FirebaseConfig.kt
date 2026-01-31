package tj.msu.backend.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.cloud.FirestoreClient
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.FileInputStream
import java.io.IOException

@Configuration
class FirebaseConfig {
    private val logger = LoggerFactory.getLogger(FirebaseConfig::class.java)

    @Bean
    fun firebaseApp(): FirebaseApp {
        try {
            val serviceAccount = FileInputStream("serviceAccountKey.json")
            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .setDatabaseUrl("https://msu-tj-backend-default-rtdb.europe-west1.firebasedatabase.app/")
                .build()

            if (FirebaseApp.getApps().isEmpty()) {
                logger.info("Initializing Firebase App...")
                return FirebaseApp.initializeApp(options)
            }
            return FirebaseApp.getInstance()
        } catch (e: IOException) {
            logger.error("Could not find serviceAccountKey.json or initialize Firebase: {}", e.message)
            throw RuntimeException(e)
        }
    }

    @Bean
    fun firebaseDatabase(firebaseApp: FirebaseApp): FirebaseDatabase {
        return FirebaseDatabase.getInstance(firebaseApp)
    }

    @Bean
    fun firestore(firebaseApp: FirebaseApp): Firestore {
        return FirestoreClient.getFirestore(firebaseApp)
    }

    @Bean
    fun firebaseMessaging(firebaseApp: FirebaseApp): FirebaseMessaging {
        return FirebaseMessaging.getInstance(firebaseApp)
    }
}
