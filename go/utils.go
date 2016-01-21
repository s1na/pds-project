package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"
)

var localIP string

const letterBytes = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

func getLocalIP() string {
	if localIP != "" {
		return localIP
	}
	i, _ := net.InterfaceByName("eth0")
	addrs, _ := i.Addrs()
	ip := addrs[0].(*net.IPNet).IP.String()
	localIP = ip
	return ip
}

func getLocalAddr() string {
	return fmt.Sprint("http://", getLocalIP(), ":", serverPort)
}

func startElection() {
	won := true
	for _, n := range network {
		if n.ID > selfNode.ID {
			fmt.Println("Sending election for ", n.ID)
			_, err := http.Get(fmt.Sprint(n.Addr, "/election"))
			if err != nil {
				panic(err)
			} else {
				won = false
				break
			}
		}
	}
	if won {
		for i, n := range network {
			if n.Addr == getLocalAddr() {
				fmt.Println("Setting self as master => ", n.Addr)
				network[i].Master = true
				masterNode = &network[i]

				masterFinishCond = sync.NewCond(&sync.Mutex{})
				masterFinished = 0
				if syncAlgorithm == "centralized" {
					masterResourceControl = make(chan string)
					masterResourceData = make(chan string)
					masterCurNode = ""
					go centralizedResourceManager()
				}
			} else {
				fmt.Println("Sending coordinator for ", n.Addr)
				coordinatorReq(n.Addr)
			}
		}
	}
}

func randStringBytes(n int) string {
	b := make([]byte, n)
	for i := range b {
		b[i] = letterBytes[rand.Intn(len(letterBytes))]
	}
	return string(b)
}

func distributedRW() {
	var addedWords []string
	var shData string

	if syncAlgorithm == "ra" {
		lc = &LamportClock{Counter: 0, ID: selfNode.ID}
		accessCond = sync.NewCond(&sync.Mutex{})
	}

	start := time.Now()
	for time.Since(start).Seconds() < 20 {
		duration := rand.Intn(10)
		fmt.Println("Sleeping for ", duration)
		time.Sleep(time.Duration(duration) * time.Second)

		if syncAlgorithm == "centralized" {
			resBody := syncCentralizedReq(masterNode.Addr)
			var data map[string]interface{}
			if err := json.Unmarshal(resBody, &data); err != nil {
				panic(err)
			}
			if !data["ok"].(bool) {
				panic(data["err"].(string))
			}

			resBody = readDataReq(masterNode.Addr)
			//var data map[string]string
			if err := json.Unmarshal(resBody, &data); err != nil {
				panic(err)
			}
			shData = data["data"].(string)
			fmt.Println("Current data, ", shData)
			randomWord := randStringBytes(4)
			addedWords = append(addedWords, randomWord)
			shData = shData + randomWord
			writeDataReq(masterNode.Addr, shData)

			resBody = syncCentralizedRelease(masterNode.Addr)
			//var data map[string]interface{}
			if err := json.Unmarshal(resBody, &data); err != nil {
				panic(err)
			}
			if !data["ok"].(bool) {
				panic(data["err"].(string))
			}
		} else if syncAlgorithm == "ra" {
			accessStatus = 1
			fmt.Println("LC ", lc.Counter, lc.ID)
			accessClock = &LamportClock{}
			*accessClock = *lc

			var data map[string]interface{}
			var wg sync.WaitGroup

			// Acquire lock
			for _, n := range network {
				if n.Addr != selfNode.Addr {
					wg.Add(1)
					go func(url string) {
						defer wg.Done()
						syncRAReq(url)
					}(masterNode.Addr)
				}
			}
			wg.Wait()
			accessStatus = 2

			resBody := readDataReq(masterNode.Addr)
			if err := json.Unmarshal(resBody, &data); err != nil {
				panic(err)
			}

			shData = data["data"].(string)
			fmt.Println("Current data, ", shData)
			randomWord := randStringBytes(4)
			addedWords = append(addedWords, randomWord)
			shData = shData + randomWord

			writeDataReq(masterNode.Addr, shData)

			// Release lock
			accessStatus = 0
			accessCond.Broadcast()
		}
	}
	var data map[string]interface{}
	resBody := finishReq(masterNode.Addr)
	if err := json.Unmarshal(resBody, &data); err != nil {
		panic(err)
	}
	shData = data["data"].(string)
	fmt.Println("Final data: ", shData)

	// Check for self added substrings
	for _, w := range addedWords {
		fmt.Printf("Check %s: %t\n", w, strings.Contains(shData, w))
	}
}

func centralizedResourceManager() {
	for {
		cmd := <-masterResourceControl
		if cmd == "req" {
			for {
				d := <-masterResourceData
				if d == "read" {
					masterResourceData <- masterSharedData
				} else if d == "write" {
					masterSharedData = <-masterResourceData
				} else if d == "release" {
					break
				} else {
					fmt.Println("Resource manager received unknown msg, abort.")
					break
				}
			}
		}
	}
}
