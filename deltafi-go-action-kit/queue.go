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
// ABOUTME: Redis/Valkey queue operations for DeltaFi action communication.
// ABOUTME: Implements BZPOPMIN-based work polling and ZADD-based result publishing.

package actionkit

import (
	"context"
	"fmt"
	"strings"
	"time"

	"github.com/redis/go-redis/v9"
)

const (
	heartbeatKey       = "org.deltafi.action-queue.heartbeat"
	longRunningTaskKey = "org.deltafi.action-queue.long-running-tasks"
)

// actionEventQueue wraps a Redis client for DeltaFi queue operations.
type actionEventQueue struct {
	client *redis.Client
}

// newActionEventQueue creates a queue client and verifies the connection.
func newActionEventQueue(url, password string) (*actionEventQueue, error) {
	host, port := parseHostPort(url)
	client := redis.NewClient(&redis.Options{
		Addr:     host + ":" + port,
		Password: password,
	})

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := client.Ping(ctx).Err(); err != nil {
		client.Close()
		return nil, fmt.Errorf("failed to connect to redis at %s: %w", url, err)
	}

	return &actionEventQueue{client: client}, nil
}

// Take blocks until an item is available on the queue, returning the raw JSON
// payload. Returns nil (not an error) on timeout. The returned slice is owned
// by the caller and may be passed directly to json.Unmarshal.
func (q *actionEventQueue) Take(ctx context.Context, queueName string) ([]byte, error) {
	return q.TakeFromAny(ctx, queueName)
}

// Put publishes data to the named queue with the current timestamp as score.
// The data is forwarded to redis without an intermediate string conversion.
func (q *actionEventQueue) Put(ctx context.Context, queueName string, data []byte) error {
	score := float64(time.Now().UnixMilli())
	return q.client.ZAdd(ctx, queueName, redis.Z{
		Score:  score,
		Member: data,
	}).Err()
}

// Heartbeat updates the heartbeat timestamp for an action.
func (q *actionEventQueue) Heartbeat(ctx context.Context, actionName string) error {
	return q.client.HSet(ctx, heartbeatKey, actionName, formatTime(time.Now())).Err()
}

// SetLongRunningTask records a long-running task execution.
func (q *actionEventQueue) SetLongRunningTask(ctx context.Context, key, value string) error {
	return q.client.HSet(ctx, longRunningTaskKey, key, value).Err()
}

// RemoveLongRunningTask removes a long-running task entry.
func (q *actionEventQueue) RemoveLongRunningTask(ctx context.Context, key string) error {
	return q.client.HDel(ctx, longRunningTaskKey, key).Err()
}

// TakeFromAny blocks until an item is available on any of the given queues.
// Returns the raw JSON payload, or nil on timeout.
func (q *actionEventQueue) TakeFromAny(ctx context.Context, queueNames ...string) ([]byte, error) {
	result, err := q.client.BZPopMin(ctx, 5*time.Second, queueNames...).Result()
	if err != nil {
		if err == redis.Nil {
			return nil, nil
		}
		return nil, err
	}
	if result == nil {
		return nil, nil
	}
	member, ok := result.Member.(string)
	if !ok {
		return nil, fmt.Errorf("unexpected member type from BZPOPMIN: %T", result.Member)
	}
	return []byte(member), nil
}

// Subscribe returns a pub/sub subscription for the given channel.
// The caller should range over the returned channel and call Close when done.
func (q *actionEventQueue) Subscribe(ctx context.Context, channel string) *redis.PubSub {
	return q.client.Subscribe(ctx, channel)
}

// Close shuts down the Redis connection.
func (q *actionEventQueue) Close() error {
	return q.client.Close()
}

// parseHostPort splits a URL or "host:port" string, stripping any scheme prefix
// and defaulting port to "6379".
func parseHostPort(addr string) (string, string) {
	// Strip scheme if present (e.g. "http://", "redis://")
	if i := strings.Index(addr, "://"); i >= 0 {
		addr = addr[i+3:]
	}
	// Reverse scan for port separator
	for i := len(addr) - 1; i >= 0; i-- {
		if addr[i] == ':' {
			return addr[:i], addr[i+1:]
		}
	}
	return addr, "6379"
}
