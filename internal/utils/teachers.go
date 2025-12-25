package utils

import (
	"msu-tj-backend/internal/models"
	"regexp"
	"strings"
	"time"
)

var teacherNameRegex = regexp.MustCompile(`([А-ЯЁ][а-яё]+[\s\xA0]+[А-ЯЁ]\.[\s\xA0]*[А-ЯЁ]\.?)`)

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

				for _, rawTeacherName := range lesson.Teacher {
					rawTeacherName = strings.TrimSpace(rawTeacherName)
					if rawTeacherName == "" {
						continue
					}

					foundNames := teacherNameRegex.FindAllString(rawTeacherName, -1)

					if len(foundNames) > 0 {
						for _, name := range foundNames {
							safeName := sanitizeName(name)
							if safeName == "" {
								continue
							}

							addLessonToTeacher(teachersMap, getOrCreateTeacher, safeName, group, lesson, dayIdx, lessonIdx)
						}
						continue
					}

					junkWords := []string{
						"английский", "немецкий", "китайский", "французский",
						"язык", "группа", "подгруппа", "физ", "пр.", "лк.", "[пз]", "(", ")",
					}

					cleanedName := rawTeacherName
					for _, junk := range junkWords {
						cleanedName = strings.ReplaceAll(cleanedName, junk, "")
						cleanedName = strings.ReplaceAll(cleanedName, strings.Title(junk), "")
						cleanedName = strings.ReplaceAll(cleanedName, strings.ToUpper(junk), "")
						if junk == "английский" {
							cleanedName = strings.ReplaceAll(cleanedName, "Английский", "")
						}
					}

					safeTeacherName := sanitizeName(cleanedName)

					if strings.TrimSpace(safeTeacherName) == "Иностранный" || len(safeTeacherName) < 3 {
						continue
					}

					addLessonToTeacher(teachersMap, getOrCreateTeacher, safeTeacherName, group, lesson, dayIdx, lessonIdx)
				}
			}
		}
	}

	result := make(map[string]models.TeacherSchedule)
	forbiddenChars := ".$#[]/"
	for name, schedule := range teachersMap {
		isValid := true
		if strings.ContainsAny(name, forbiddenChars) {
			isValid = false
		}
		if name == "" {
			isValid = false
		}
		for _, char := range name {
			if char < 32 {
				isValid = false
				break
			}
		}
		if isValid {
			result[name] = *schedule
		}
	}
	return result
}

func addLessonToTeacher(
	teachersMap map[string]*models.TeacherSchedule,
	getOrCreate func(string) *models.TeacherSchedule,
	name string,
	group models.GroupSchedule,
	lesson *models.Lesson,
	dayIdx, lessonIdx int,
) {
	tSchedule := getOrCreate(name)
	existingLesson := tSchedule.Days[dayIdx].Lessons[lessonIdx]

	if existingLesson != nil {
		alreadyAdded := false
		for _, g := range existingLesson.Teacher {
			if g == group.Title {
				alreadyAdded = true
				break
			}
		}
		if !alreadyAdded {
			existingLesson.Teacher = append(existingLesson.Teacher, group.Title)
		}
	} else {
		teacherLesson := &models.Lesson{
			Subject: lesson.Subject,
			Type:    lesson.Type,
			Rooms:   lesson.Rooms,
			Teacher: []string{group.Title},
		}
		tSchedule.Days[dayIdx].Lessons[lessonIdx] = teacherLesson
	}
}

func sanitizeName(name string) string {
	name = strings.ReplaceAll(name, "\x00", "")
	name = strings.TrimSpace(name)
	if idx := strings.Index(name, "("); idx != -1 {
	}

	name = strings.ReplaceAll(name, ".", "_")
	name = strings.ReplaceAll(name, "/", "-")
	name = strings.ReplaceAll(name, "#", "")
	name = strings.ReplaceAll(name, "$", "")
	name = strings.ReplaceAll(name, "[", "(")
	name = strings.ReplaceAll(name, "]", ")")
	name = strings.ReplaceAll(name, "\n", "")
	name = strings.ReplaceAll(name, "\r", "")
	name = strings.ReplaceAll(name, "\t", "")
	name = strings.Trim(name, ":,; ")
	return strings.TrimSpace(name)
}

func getDayNameByIndex(i int) string {
	days := []string{"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"}
	if i >= 0 && i < len(days) {
		return days[i]
	}
	return ""
}
