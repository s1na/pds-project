package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"net"
	"net/http"
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
	if selfNode == masterNode {
		fmt.Println("Distributed RW started, I am master.")
	} else {
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
				shData := data["data"].(string)
				fmt.Println("Current data, ", shData)
				shData = shData + randStringBytes(4)
				writeDataReq(masterNode.Addr, shData)

				resBody = syncCentralizedRelease(masterNode.Addr)
				//var data map[string]interface{}
				if err := json.Unmarshal(resBody, &data); err != nil {
					panic(err)
				}
				if !data["ok"].(bool) {
					panic(data["err"].(string))
				}
			}
		}
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
