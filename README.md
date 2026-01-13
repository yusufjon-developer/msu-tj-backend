# 🎓 MSU TJ - Backend Service

![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)
![Firebase](https://img.shields.io/badge/Firebase-Admin-orange.svg)
![License](https://img.shields.io/badge/License-Proprietary-red.svg)

Backend service for the **[MSU TJ Android Client](https://github.com/msu-tj/android-client)** (Lomonosov Moscow State University in Dushanbe).

## Overview
This service acts as the central intelligence for the MSU TJ ecosystem. It autonomously monitors university servers, processes schedule files, and synchronizes structured data to the cloud for client consumption.

## Key Features
- **Smart Polling**: Monitors `enf.xls` and `gf.xls` for changes using `Last-Modified` headers.
- **Advanced Parsing**:
    - Supports both legacy `.xls` and modern `.xlsx`.
    - Extract dates automatically from schedule headers.
    - Handles complex merged cells and Russian date formats.
- **Next Week Preview**: Intelligently detects "next week" schedules and separates them from the current week, allowing students to plan ahead.
- **History Archiving**: Automatically archives weekly snapshots to **Cloud Firestore**.
- **Realtime Sync**: Pushes "hot" data to **Firebase Realtime Database**.

## Tech Stack
*   **Language**: Kotlin 2.1
*   **Framework**: Spring Boot 3.4
*   **Build Tool**: Gradle (Kotlin DSL)
*   **Parser**: Apache POI
*   **Database**:
    *   **Realtime DB**: Current & Next week schedules.
    *   **Firestore**: Historical archives.
*   **Deployment**: Docker container on Fly.io.

## Getting Started

### Prerequisites
*   JDK 17+
*   Firebase Project with Realtime Database and Firestore enabled.
*   `serviceAccountKey.json` placed in the project root.

### Running Locally
```bash
# Clone repository
git clone https://github.com/yusufjon-developer/msu-tj-backend.git

# Run with Gradle
./gradlew bootRun
```

## Deployment
The project is configured for continuous deployment to Fly.io via GitHub Actions.
1.  Push to `master`.
2.  The workflow builds the Docker image and deploys it automatically.

---
**License**: Proprietary. See [LICENSE](LICENSE) for details.
&copy; 2026 Yusufjon. All rights reserved.
