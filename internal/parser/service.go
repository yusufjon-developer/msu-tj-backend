package parser

import (
	"bytes"
	"fmt"
	"log"
	"regexp"
	"strings"

	"msu-tj-backend/internal/models"

	"github.com/extrame/xls"
)

var (
	roomRegex    = regexp.MustCompile(`\b\d{3}\b`)
	courseRegex  = regexp.MustCompile(`(\d+)\s*КУРС`)
	typeRegex    = regexp.MustCompile(`\[(.*?)\]`)
	teacherRegex = regexp.MustCompile(`\((.*?)\)`)

	romanToArabic = map[string]string{
		"I": "1", "II": "2", "III": "3", "IV": "4", "V": "5", "VI": "6",
		"1": "1", "2": "2", "3": "3", "4": "4", "5": "5", "6": "6",
	}

	directionCodes = map[string]string{
		"ПРИКЛАДНАЯ":      "pmi",
		"ХИМИЯ":           "hfmm",
		"ГЕОЛОГИЯ":        "geo",
		"МЕЖДУНАРОДНЫЕ":   "mo",
		"ЛИНГВИСТИКА":     "ling",
		"ГОСУДАРСТВЕННОЕ": "gmu",
	}

	daysCodes = []string{"1", "2", "3", "4", "5", "6", "7"}
)

func ParseXLS(fileBytes []byte, globalData map[string]models.GroupSchedule) error {
	reader := bytes.NewReader(fileBytes)

	xlFile, err := xls.OpenReader(reader, "windows-1251")
	if err != nil {
		return fmt.Errorf("xls open error: %w", err)
	}

	for i := 0; i < xlFile.NumSheets(); i++ {
		sheet := xlFile.GetSheet(i)
		if sheet == nil {
			continue
		}

		var currentGroupID string
		var currentGroupTitle string

		for r := 0; r <= int(sheet.MaxRow); r++ {
			row := sheet.Row(r)
			if row == nil {
				continue
			}

			fullRowText := ""
			for c := 0; c < row.LastCol(); c++ {
				fullRowText += " " + row.Col(c)
			}
			fullRowText = strings.ToUpper(strings.TrimSpace(fullRowText))

			isHeaderCandidate := strings.Contains(fullRowText, "КУРС") && !strings.Contains(fullRowText, "ПРАКТИЧЕСКИЙ")

			if isHeaderCandidate {
				foundCode := ""
				for key, code := range directionCodes {
					if strings.Contains(fullRowText, key) {
						foundCode = code
						break
					}
				}

				matches := courseRegex.FindStringSubmatch(fullRowText)

				if foundCode != "" && len(matches) > 1 {
					courseNum := matches[1]
					currentGroupID = fmt.Sprintf("%s_%s", foundCode, courseNum)
					currentGroupTitle = formatTitle(foundCode, courseNum)

					if _, exists := globalData[currentGroupID]; !exists {
						globalData[currentGroupID] = models.GroupSchedule{
							ID:    currentGroupID,
							Title: currentGroupTitle,
							Days:  make(map[string]models.DaySchedule),
						}
						log.Printf("Group parsed: %s", currentGroupTitle)
					}
					continue
				}
			}

			if currentGroupID != "" {
				firstCell := strings.TrimSpace(row.Col(0))
				firstCell = strings.Trim(firstCell, ". ")
				pairNum, isPair := romanToArabic[firstCell]

				if isPair {
					for dIndex, dayCode := range daysCodes {
						subjCol := dIndex*2 + 1
						roomCol := dIndex*2 + 2
						if roomCol <= row.LastCol() {
							subjText := strings.TrimSpace(row.Col(subjCol))
							roomText := strings.TrimSpace(row.Col(roomCol))

							if subjText != "" {
								lesson := parseLessonString(subjText, roomText)
								group := globalData[currentGroupID]
								if group.Days == nil {
									group.Days = make(map[string]models.DaySchedule)
								}
								daySched, ok := group.Days[dayCode]
								if !ok {
									daySched = models.DaySchedule{
										Day:     getDayName(dayCode),
										Lessons: make(map[string]models.Lesson),
									}
								}
								if daySched.Lessons == nil {
									daySched.Lessons = make(map[string]models.Lesson)
								}
								daySched.Lessons[pairNum] = lesson
								group.Days[dayCode] = daySched
								globalData[currentGroupID] = group
							}
						}
					}
				}
			}
		}
	}
	return nil
}

func parseLessonString(subjRaw, roomRaw string) models.Lesson {
	lesson := models.Lesson{}
	typeMatch := typeRegex.FindStringSubmatch(subjRaw)
	if len(typeMatch) > 1 {
		lesson.Type = cleanType(typeMatch[1])
		subjRaw = strings.Replace(subjRaw, typeMatch[0], "", 1)
	}
	teacherMatch := teacherRegex.FindStringSubmatch(subjRaw)
	if len(teacherMatch) > 1 {
		teachersStr := teacherMatch[1]
		parts := strings.Split(teachersStr, ",")
		for _, t := range parts {
			lesson.Teacher = append(lesson.Teacher, strings.TrimSpace(t))
		}
		subjRaw = strings.Replace(subjRaw, teacherMatch[0], "", 1)
	}
	lesson.Subject = cleanSubject(subjRaw)
	lesson.Rooms = parseRooms(roomRaw)
	return lesson
}

func parseRooms(text string) []string {
	var rooms []string
	matches := roomRegex.FindAllString(text, -1)
	rooms = append(rooms, matches...)
	lower := strings.ToLower(text)
	if strings.Contains(lower, "физ") {
		rooms = append(rooms, "лабФИЗ")
	}
	if strings.Contains(lower, "хим") {
		rooms = append(rooms, "лабХИМ")
	}
	if strings.Contains(lower, "гео") {
		rooms = append(rooms, "лабГЕО")
	}
	if strings.Contains(lower, "стд") {
		rooms = append(rooms, "стд")
	}
	unique := make(map[string]bool)
	var final []string
	for _, r := range rooms {
		r = strings.TrimSuffix(r, ".0")
		if !unique[r] {
			unique[r] = true
			final = append(final, r)
		}
	}
	return final
}

func cleanType(t string) string {
	t = strings.ToUpper(t)
	if strings.Contains(t, "ЛК") {
		return "Лекция"
	}
	if strings.Contains(t, "ПЗ") {
		return "Практика"
	}
	if strings.Contains(t, "СЕМИНАР") {
		return "Семинар"
	}
	if strings.Contains(t, "ЗАЧЕТ") {
		return "Зачет"
	}
	if strings.Contains(t, "ЭКЗАМЕН") {
		return "Экзамен"
	}
	return t
}

func cleanSubject(s string) string {
	s = strings.TrimSpace(s)
	spaceRegex := regexp.MustCompile(`\s+`)
	s = spaceRegex.ReplaceAllString(s, " ")
	return s
}

func formatTitle(code, course string) string {
	base := code
	switch code {
	case "pmi":
		base = "ПМИ"
	case "hfmm":
		base = "ХФММ"
	case "geo":
		base = "Геология"
	case "mo":
		base = "МО"
	case "ling":
		base = "Лингвистика"
	case "gmu":
		base = "ГМУ"
	}
	return fmt.Sprintf("%s, %s курс", base, course)
}

func getDayName(code string) string {
	switch code {
	case "1":
		return "Понедельник"
	case "2":
		return "Вторник"
	case "3":
		return "Среда"
	case "4":
		return "Четверг"
	case "5":
		return "Пятница"
	case "6":
		return "Суббота"
	case "7":
		return "Воскресенье"
	}
	return ""
}
