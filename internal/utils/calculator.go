package utils

import (
	"msu-tj-backend/internal/models"
)

var daysToCheck = []string{"1", "2", "3", "4", "5", "6", "7"}
var pairsToCheck = []string{"1", "2", "3", "4", "5"}

var AllRooms = []string{
	"100", "101", "102", "103", "104", "105", "106", "107", "108",
	"208",
	"301", "302",
	"401", "402", "403", "404",
	"601", "602", "603",
	"701", "702", "703", "704",
	"801", "802",
	"лабГЕОЛ", "лабФИЗ", "лабХИМ", "стд",
}

func CalculateFreeRooms(groups map[string]models.GroupSchedule) models.FreeRoomsData {
	scheduleMap := make(map[string]map[string][]string)

	for i, dayKey := range daysToCheck {
		scheduleMap[dayKey] = make(map[string][]string)

		for j, pairKey := range pairsToCheck {
			occupiedSet := make(map[string]bool)

			for _, group := range groups {
				if i < len(group.Days) && group.Days[i] != nil {
					daySched := group.Days[i]
					if j < len(daySched.Lessons) {
						lesson := daySched.Lessons[j]
						if lesson != nil {
							for _, room := range lesson.Rooms {
								occupiedSet[room] = true
							}
						}
					}
				}
			}

			var free []string
			for _, room := range AllRooms {
				if !occupiedSet[room] {
					free = append(free, room)
				}
			}

			scheduleMap[dayKey][pairKey] = free
		}
	}

	return models.FreeRoomsData{
		Schedule:   scheduleMap,
		LastUpdate: "",
	}
}
