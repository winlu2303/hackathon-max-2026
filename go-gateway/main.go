package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/nats-io/nats.go"
)

type Update struct {
	UpdateType string          `json:"update_type"`
	Timestamp  int64           `json:"timestamp"`
	UserID     int64           `json:"user_id"`
	Payload    json.RawMessage `json:"payload"`
}

var nc *nats.Conn

func main() {
	var err error
	natsURL := getEnv("NATS_URL", "nats://nats:4222")
	nc, err = nats.Connect(natsURL, nats.MaxReconnects(-1), nats.ReconnectWait(2*time.Second))
	if err != nil {
		log.Fatalf("nats connect error: %v", err)
	}
	defer nc.Close()

	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.POST("/webhook/max", handleWebhook)
	r.GET("/health", func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"status": "ok"})
	})

	port := getEnv("PORT", "8080")
	log.Printf("go-gateway listening on :%s", port)
	if err := r.Run(":" + port); err != nil {
		log.Fatal(err)
	}
}

func handleWebhook(c *gin.Context) {
	secret := c.GetHeader("X-Max-Bot-Api-Secret")
	expected := os.Getenv("MAX_WEBHOOK_SECRET")
	if expected != "" && secret != expected {
		c.JSON(http.StatusUnauthorized, gin.H{"error": "unauthorized"})
		return
	}

	body, err := c.GetRawData()
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "cannot read body"})
		return
	}

	var update Update
	if err := json.Unmarshal(body, &update); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "invalid json"})
		return
	}

	topic := "normal"
	if isEmergency(body) {
		topic = "emergency"
	}

	if err := nc.Publish(topic, body); err != nil {
		log.Printf("nats publish error: %v", err)
		c.JSON(http.StatusInternalServerError, gin.H{"error": "queue error"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"ok": true})
}

func isEmergency(body []byte) bool {
	return strings.Contains(string(body), "emergency_") ||
		strings.Contains(string(body), "\"FLOOD\"") ||
		strings.Contains(string(body), "\"FIRE\"") ||
		strings.Contains(string(body), "\"POWER\"") ||
		strings.Contains(string(body), "\"BLOCKED\"")
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
