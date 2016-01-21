package main

import (
	"math"
	"sync/atomic"
)

type LamportClock struct {
	Counter uint64 `json:"counter"`
	ID      int    `json:"id"`
}

func (lc *LamportClock) Time() uint64 {
	return atomic.LoadUint64(&lc.Counter)
}

func (lc *LamportClock) Increment() uint64 {
	return atomic.AddUint64(&lc.Counter, 1)
}

func (lc *LamportClock) Set(val uint64) {
	atomic.StoreUint64(&lc.Counter, val)
}

func (lc *LamportClock) Recv(val uint64) {
	lc.Set(uint64(math.Max(float64(lc.Counter), float64(val))) + 1)
}

func (lc *LamportClock) Compare(other *LamportClock) int {
	if lc.Counter > other.Counter {
		return 1
	} else if lc.Counter < other.Counter {
		return -1
	} else {
		if lc.ID > other.ID {
			return 1
		} else if lc.ID < other.ID {
			return -1
		} else {
			return 0
		}
	}
}
