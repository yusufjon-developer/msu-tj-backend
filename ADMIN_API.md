# Admin Notification API Documentation

**Base URL**: `/api/v1/admin`

This API allows the Administrator/Teacher to manage and send notifications to users.

## 1. Get Available Topics

Retrieve a list of all available topics for targeting.

*   **Endpoint:** `GET /notifications/topics`
*   **Response:** `200 OK`
*   **Body Example:**
    ```json
    [
      {
        "id": "global",
        "name": "Все пользователи",
        "description": "Все студенты и преподаватели"
      },
      {
        "id": "teachers",
        "name": "Преподаватели",
        "description": "Только преподаватели"
      },
      {
        "id": "students",
        "name": "Студенты",
        "description": "Все студенты (и пользователи без роли)"
      },
      {
        "id": "pmi_1",
        "name": "PMI 1 курс",
        "description": "Группа pmi, 1 курс"
      }
    ]
    ```

## 2. Get Users

Search and retrieve users to find specific targets.

*   **Endpoint:** `GET /users`
*   **Query Params:**
    *   `email` (Optional): Prefix search by email.
    *   `limit` (Optional, default=20): Number of results.
*   **Response:** `200 OK`
*   **Body Example:**
    ```json
    [
      {
        "uid": "abc123xyz",
        "email": "student@msu.tj",
        "name": "John Doe",
        "facultyCode": "pmi",
        "course": 1,
        "role": "student"
      }
    ]
    ```

## 3. Send Notification

Send a Push Notification and optionally save it to history.

*   **Endpoint:** `POST /notifications/send`
*   **Headers:** `Content-Type: application/json`
*   **Request Body:**
    ```json
    {
      "title": "Welcome",
      "body": "Hello World!",
      "targetType": "group", 
      "targetValue": "pmi_1",
      "saveToHistory": true
    }
    ```

### Field Descriptions:

| Field | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `title` | String | Yes | Notification title |
| `body` | String | Yes | Notification message text |
| `targetType` | String | Yes | One of: `global`, `teachers`, `students`, `group`, `user` |
| `targetValue` | String | Semi | Required for `group` (e.g. `pmi_1`) or `user` (UID). Null for others. |
| `saveToHistory`| Boolean| No | Default `true`. If true, notification is saved to user's Firestore history. |

### Valid Target Types:
*   `global`: Sends to all users.
*   `teachers`: Sends to teachers only.
*   `students`: Sends to students only (and users with no role).
*   `group`: Sends to specific faculty group (requires `targetValue` like `pmi_1`).
*   `user`: Sends to specific user (requires `targetValue` as UID). *Note: Push depends on token availability logic, history saving is guaranteed.*
