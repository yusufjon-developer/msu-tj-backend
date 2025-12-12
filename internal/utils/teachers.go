package utils

import (
	"fmt"
	"msu-tj-backend/internal/models"
	"strings"
	"time"
)

func ExtractTeachers(groups map[string]models.GroupSchedule) map[string]models.TeacherSchedule {
	teachersMap := make(map[string]*models.TeacherSchedule)

	getOrCreateTeacher := func(name string) *models.TeacherSchedule {
		if _, exists := teachersMap[name]; !exists {
			days := make([]*models.DaySchedule, 7)
			for i := 0; i < 7; i++ {
				days[i] = &models.DaySchedule{
					Day:     getDayNameByIndex(i),
					Lessons: make([]*models.Lesson, 5),
				}
			}
			teachersMap[name] = &models.TeacherSchedule{
				Name:      name,
				UpdatedAt: time.Now().Format("2006-01-02 15:04:05"),
				Days:      days,
			}
		}
		return teachersMap[name]
	}

	for _, group := range groups {
		for dayIdx, day := range group.Days {
			if day == nil {
				continue
			}

			for lessonIdx, lesson := range day.Lessons {
				if lesson == nil {
					continue
				}

				for _, teacherName := range lesson.Teacher {
					teacherName = strings.TrimSpace(teacherName)
					if teacherName == "" {
						continue
					}

					tSchedule := getOrCreateTeacher(teacherName)

					teacherLesson := &models.Lesson{
						Subject: fmt.Sprintf("%s (%s)", lesson.Subject, group.Title),
						Type:    lesson.Type,
						Rooms:   lesson.Rooms,
						Teacher: []string{group.Title},
					}

					tSchedule.Days[dayIdx].Lessons[lessonIdx] = teacherLesson
				}
			}
		}
	}

	result := make(map[string]models.TeacherSchedule)
	for name, schedule := range teachersMap {
		result[name] = *schedule
	}

	return result
}

func getDayNameByIndex(i int) string {
	days := []string{"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"}
	if i >= 0 && i < len(days) {
		return days[i]
	}
	return ""
}
