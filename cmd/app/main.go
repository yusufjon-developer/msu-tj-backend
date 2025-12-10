package main

import (
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"time"

	"msu-tj-backend/internal/models"
	"msu-tj-backend/internal/parser"
	"msu-tj-backend/internal/storage"
	"msu-tj-backend/internal/utils"
)

const (
	pollingInterval = 30 * time.Second
	firebaseURL     = "https://msu-tj-backend-default-rtdb.europe-west1.firebasedatabase.app/"
)

var scheduleURLs = []string{
	"https://msu.tj/file/timetable/enf.xls",
	"https://msu.tj/file/timetable/gf.xls",
}

func main() {
	log.Println("=== MSU TJ Backend Starting (Smart Polling Mode) ===")

	store, err := storage.NewFirebase("serviceAccountKey.json", firebaseURL)
	if err != nil {
		log.Fatalf("CRITICAL: Firebase connection error: %v", err)
	}
	log.Println("Firebase connected successfully")

	go startHealthServer()

	runWorkerLoop(store)
}

func runWorkerLoop(store *storage.Service) {
	lastModifiedMap := make(map[string]string)

	for {
		needUpdate := false

		for _, url := range scheduleURLs {
			newLastMod, changed := checkFileHeader(url, lastModifiedMap[url])

			if changed {
				lastModifiedMap[url] = newLastMod
				needUpdate = true
			}
		}

		if needUpdate {
			log.Println("Starting database update process...")
			start := time.Now()

			allGroupsData := make(map[string]models.GroupSchedule)
			successCount := 0

			for _, url := range scheduleURLs {
				err := processFile(url, allGroupsData)
				if err != nil {
					log.Printf("Error processing %s: %v", url, err)
				} else {
					successCount++
				}
			}

			if successCount > 0 && len(allGroupsData) > 0 {
				freeRoomsData := utils.CalculateFreeRooms(allGroupsData)

				err := store.SaveFullUpdate(allGroupsData, freeRoomsData)
				if err != nil {
					log.Printf("Error saving to Firebase: %v", err)
				} else {
					log.Printf("Success. Update finished in %v. Total groups: %d", time.Since(start), len(allGroupsData))
				}
			} else {
				log.Println("No valid data received, skipping database update.")
			}
		}

		time.Sleep(pollingInterval)
	}
}

func checkFileHeader(url string, oldLastModified string) (string, bool) {
	client := http.Client{Timeout: 10 * time.Second}

	req, _ := http.NewRequest("HEAD", url, nil)
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	start := time.Now()
	resp, err := client.Do(req)
	duration := time.Since(start)

	if err != nil {
		log.Printf("[HEAD] Request error for %s: %v", url, err)
		return oldLastModified, false
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		log.Printf("[HEAD] %s | Status: %d | Time: %v", url, resp.StatusCode, duration)
		return oldLastModified, false
	}

	newLastModified := resp.Header.Get("Last-Modified")

	if oldLastModified == "" {
		log.Printf("[HEAD] %s | Time: %v | Initial fetch -> Update required", url, duration)
		return newLastModified, true
	}

	if newLastModified == oldLastModified {
		log.Printf("[HEAD] %s | Time: %v | Date: %s | No changes", url, duration, newLastModified)
		return oldLastModified, false
	}

	log.Printf("[HEAD] %s | Time: %v | Old: %s -> New: %s | Update detected", url, duration, oldLastModified, newLastModified)
	return newLastModified, true
}

func processFile(url string, data map[string]models.GroupSchedule) error {
	client := http.Client{Timeout: 60 * time.Second}

	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	resp, err := client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	if resp.StatusCode != 200 {
		return fmt.Errorf("bad status code: %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return err
	}

	return parser.ParseXLS(bodyBytes, data)
}

func startHealthServer() {
	http.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(200)
		w.Write([]byte("OK"))
	})

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}
	log.Printf("Health server listening on port %s", port)
	http.ListenAndServe(":"+port, nil)
}
