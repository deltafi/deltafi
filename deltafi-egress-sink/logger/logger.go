/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2024 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package logger

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"time"
)

type CustomLog struct {
	Timestamp  time.Time `json:"timestamp"`
	Level      string    `json:"level"`
	LoggerName string    `json:"loggerName"`
	HostName   string    `json:"hostName"`
	Message    string    `json:"message"`
}

var hostname string

func init() {
	var err error
	hostname, err = os.Hostname()
	if err != nil {
		log.Fatalf("Failed to get hostname: %v", err)
	}

	log.SetOutput(os.Stdout)
	log.SetFlags(0) // Turn off default timestamp
}

func Log(level, msg string, v ...interface{}) {
	logEntry := CustomLog{
		Timestamp:  time.Now(),
		Level:      level,
		LoggerName: "deltafi-egress-sink",
		HostName:   hostname,
		Message: fmt.Sprintf(msg, v...),
	}

	jsonLog, err := json.Marshal(logEntry)
	if err != nil {
		log.Printf("Error marshaling log entry: %v", err)
		return
	}

	log.Println(string(jsonLog))
}

func Info(msg string, v ...interface{}) {
	Log("INFO", msg, v...)
}

func Debug(msg string, v ...interface{}) {
	Log("DEBUG", msg, v...)
}

func Warn(msg string, v ...interface{}) {
	Log("WARN", msg, v...)
}

func Error(msg string, v ...interface{}) {
	Log("ERROR", msg, v...)
}