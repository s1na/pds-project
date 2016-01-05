package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"
)

var localIP string

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

func joinReq(dest string, selfAddr string) []byte {
	data := map[string]string{"addr": selfAddr}
	msg, _ := json.Marshal(data)
	resp, err := http.Post(fmt.Sprint(dest, "/join"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
	fmt.Println(resp.Body)
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}

func networkUpdateReq(dest string, network []Node) []byte {
	msg, _ := json.Marshal(network)
	resp, err := http.Post(fmt.Sprint(dest, "/network/update"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
	fmt.Println(resp.Body)
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
