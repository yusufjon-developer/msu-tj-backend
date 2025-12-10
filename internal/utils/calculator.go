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
	"лабГЕО", "лабФИЗ", "лабХИМ", "стд",
}

func CalculateFreeRooms(groups map[string]models.GroupSchedule) models.FreeRoomsData {
	scheduleMap := make(map[string]map[string][]string)

	for _, day := range daysToCheck {
		scheduleMap[day] = make(map[string][]string)

		for _, pair := range pairsToCheck {
			occupiedSet := make(map[string]bool)

			for _, group := range groups {
				if daySched, ok := group.Days[day]; ok {
					if lesson, ok := daySched.Lessons[pair]; ok {
						for _, room := range lesson.Rooms {
							occupiedSet[room] = true
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

			scheduleMap[day][pair] = free
		}
	}

	return models.FreeRoomsData{
		Schedule:   scheduleMap,
		LastUpdate: "",
	}
}
