/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
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
// ABOUTME: Logging types for action result messages.
// ABOUTME: Defines LogSeverity constants and LogMessage struct matching the DeltaFi wire format.

package actionkit

import (
	"log"
	"log/slog"
	"os"
	"sync"
	"time"
)

var configureStructuredLoggingOnce sync.Once

func configureStructuredLogging() {
	configureStructuredLoggingOnce.Do(func() {
		handler := slog.NewJSONHandler(os.Stderr, &slog.HandlerOptions{AddSource: true})
		logger := slog.New(handler).With("logger", "deltafi-go-action-kit")
		slog.SetDefault(logger)
		log.SetFlags(0)
		log.SetPrefix("")
		log.SetOutput(slog.NewLogLogger(logger.Handler(), slog.LevelInfo).Writer())
	})
}

// LogSeverity represents the severity level of a log message.
type LogSeverity string

const (
	// SeverityInfo indicates an informational message.
	SeverityInfo LogSeverity = "INFO"
	// SeverityWarning indicates a warning message.
	SeverityWarning LogSeverity = "WARNING"
	// SeverityError indicates an error message.
	SeverityError LogSeverity = "ERROR"
)

// LogMessage is a structured log entry included in action results.
type LogMessage struct {
	Severity LogSeverity `json:"severity"`
	Created  string      `json:"created"`
	Source   string      `json:"source"`
	Message  string      `json:"message"`
}

// newLogMessage creates a LogMessage with the current timestamp.
func newLogMessage(severity LogSeverity, source, message string) LogMessage {
	return LogMessage{
		Severity: severity,
		Created:  formatTime(time.Now()),
		Source:   source,
		Message:  message,
	}
}
