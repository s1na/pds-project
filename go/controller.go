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

	var id int = 0
	for _, n := range network {
		if n.ID >= id {
			id = n.ID + 1
		}
	}
	newNode := Node{ID: id, Addr: data["addr"].(string), Master: false}
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
func ElectionCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	won := true
	for _, n := range network {
		if n.ID > selfID {
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

func CoordinatorCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
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

	fmt.Println(data)
	for i, n := range network {
		if n.Addr == data["addr"] {
			fmt.Println("Setting coordinator ", data["addr"])
			network[i].Master = true
			masterNode = &network[i]
		}
	}
}
