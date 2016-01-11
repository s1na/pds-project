package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
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
			resBody := readDataReq(masterNode.Addr)
			var data map[string]string
			if err := json.Unmarshal(resBody, &data); err != nil {
				panic(err)
			}
			shData := data["data"]
			fmt.Println("Current data, ", shData)
			shData = shData + randStringBytes(4)
			writeDataReq(masterNode.Addr, shData)
		}
	}

}

func joinReq(dest string, selfAddr string) []byte {
	data := map[string]string{"addr": selfAddr}
	msg, _ := json.Marshal(data)
	resp, err := http.Post(fmt.Sprint(dest, "/join"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}

func networkUpdateReq(dest string, network []Node) []byte {
	msg, _ := json.Marshal(network)
	resp, err := http.Post(fmt.Sprint(dest, "/network/update"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}

func coordinatorReq(dest string) {
	data := map[string]string{"addr": getLocalAddr()}
	msg, _ := json.Marshal(data)
	_, err := http.Post(fmt.Sprint(dest, "/coordinator"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
}

func readDataReq(dest string) []byte {
	resp, err := http.Get(fmt.Sprint(dest, "/read"))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}

func writeDataReq(dest string, shData string) []byte {
	data := map[string]string{"data": shData}
	msg, _ := json.Marshal(data)
	resp, err := http.Post(fmt.Sprint(dest, "/write"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}
