package models

type Lesson struct {
	Subject string   `json:"subject"`
	Type    string   `json:"type"`
	Teacher []string `json:"teacher,omitempty"`
	Rooms   []string `json:"rooms,omitempty"`
}

type DaySchedule struct {
	Day     string            `json:"day"`
	Date    string            `json:"date,omitempty"`
	Lessons map[string]Lesson `json:"lessons"`
}

type GroupSchedule struct {
	ID        string                 `json:"id"`
	Title     string                 `json:"title"`
	UpdatedAt string                 `json:"updated_at"`
	Days      map[string]DaySchedule `json:"days"`
}

type FreeRoomsData struct {
	Schedule   map[string]map[string][]string `json:"schedule"`
	LastUpdate string                         `json:"last_update"`
}

type FacultyMeta struct {
	Code       string                   `json:"code"`
	Title      string                   `json:"title"`
	Directions map[string]DirectionMeta `json:"directions"`
}

type DirectionMeta struct {
	Code    string `json:"code"`
	Title   string `json:"title"`
	Courses []int  `json:"courses"`
}
