package storage

import (
	"context"
	"fmt"
	"log"
	"time"

	"msu-tj-backend/internal/models"

	firebase "firebase.google.com/go/v4"
	"firebase.google.com/go/v4/db"
	"google.golang.org/api/option"
)

type Service struct {
	client *db.Client
	ctx    context.Context
}

func NewFirebase(credFile string, dbURL string) (*Service, error) {
	ctx := context.Background()
	sa := option.WithCredentialsFile(credFile)
	conf := &firebase.Config{DatabaseURL: dbURL}

	app, err := firebase.NewApp(ctx, conf, sa)
	if err != nil {
		return nil, fmt.Errorf("error init app: %v", err)
	}

	client, err := app.Database(ctx)
	if err != nil {
		return nil, fmt.Errorf("error init db: %v", err)
	}

	return &Service{client: client, ctx: ctx}, nil
}

func (s *Service) SaveFullUpdate(
	groups map[string]models.GroupSchedule,
	freeRooms models.FreeRoomsData,
	teachers map[string]models.TeacherSchedule,
) error {

	if err := s.client.NewRef("schedules").Set(s.ctx, groups); err != nil {
		return fmt.Errorf("failed to save schedules: %v", err)
	}

	if err := s.client.NewRef("free_rooms").Set(s.ctx, freeRooms); err != nil {
		return fmt.Errorf("failed to save free rooms: %v", err)
	}

	if err := s.client.NewRef("teachers").Set(s.ctx, teachers); err != nil {
		return fmt.Errorf("failed to save teachers: %v", err)
	}

	timestamp := time.Now().Format("2006-01-02 15:04:05")
	s.client.NewRef("last_global_update").Set(s.ctx, timestamp)

	log.Println("Data (Groups, FreeRooms, Teachers) successfully sent to Firebase")
	return nil
}
