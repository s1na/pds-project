package main

import (
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net"
	"net/http"

	"github.com/julienschmidt/httprouter"
)

func JoinCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	//ip, _, _ := net.SplitHostPort(r.RemoteAddr)
	var data map[string]interface{}
	body, err := ioutil.ReadAll(r.Body)
	if err != nil {
		panic(err)
	}
	if err := r.Body.Close(); err != nil {
		panic(err)
	}
	if err := json.Unmarshal(body, &data); err != nil {
		panic(err)
	}
	fmt.Println("Join req from: ", data["addr"].(string))

	newNode := Node{Addr: data["addr"].(string), Master: false}
	network = append(network, newNode)

	w.Header().Set("Content-Type", "application/json; charset=UTF-8")
	if err := json.NewEncoder(w).Encode(network); err != nil {
		panic(err)
	}

	fmt.Println("Sending updates")
	for _, n := range network {
		if n.Addr != newNode.Addr && n.Addr != net.JoinHostPort(getLocalIP(), serverPort) {
			fmt.Println("Sending update for ", n.Addr)
			networkUpdateReq(n.Addr, network)
		}
	}
}

func NetworkUpdateCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	body, err := ioutil.ReadAll(r.Body)
	if err != nil {
		panic(err)
	}
	if err := r.Body.Close(); err != nil {
		panic(err)
	}
	if err := json.Unmarshal(body, &network); err != nil {
		panic(err)
	}
	fmt.Println("Network updated.")
}
