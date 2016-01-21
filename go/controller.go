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
	startElection()
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

func StartCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	fmt.Println("Start.")

	go distributedRW()
}

func FinishCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	masterFinishCond.L.Lock()
	masterFinished++
	boundary := len(network) - 1
	if syncAlgorithm == "ra" {
		boundary = len(network)
	}
	if masterFinished == boundary {
		masterFinishCond.Broadcast()
	} else {
		masterFinishCond.Wait()
	}
	masterFinishCond.L.Unlock()
	data := map[string]interface{}{"ok": true, "err": "", "data": masterSharedData}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	if err := json.NewEncoder(w).Encode(data); err != nil {
		panic(err)
	}
}

func ReadCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	data := map[string]interface{}{"ok": true, "err": "", "data": ""}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	if syncAlgorithm == "centralized" {
		if r.RemoteAddr == masterCurNode {
			masterResourceData <- "read"
			d := <-masterResourceData
			data["data"] = d
		} else {
			data["ok"] = false
			data["err"] = "Permission Denied."
		}
	} else if syncAlgorithm == "ra" {
		data["data"] = masterSharedData
	}

	if err := json.NewEncoder(w).Encode(data); err != nil {
		panic(err)
	}
}

func WriteCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
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

	resData := map[string]interface{}{"ok": true, "err": ""}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	if syncAlgorithm == "centralized" {
		if r.RemoteAddr == masterCurNode {
			masterResourceData <- "write"
			masterResourceData <- data["data"].(string)
		} else {
			resData["ok"] = false
			resData["err"] = "Permission Denied."
		}
	} else if syncAlgorithm == "ra" {
		masterSharedData = data["data"].(string)
	}

	if err := json.NewEncoder(w).Encode(data); err != nil {
		panic(err)
	}
}

func CentralizedReqCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	data := map[string]interface{}{"ok": true, "err": ""}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	masterResourceControl <- "req"
	masterCurNode = r.RemoteAddr

	if err := json.NewEncoder(w).Encode(data); err != nil {
		panic(err)
	}
}

func CentralizedReleaseCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	data := map[string]interface{}{"ok": true, "err": ""}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	if masterCurNode == r.RemoteAddr {
		masterResourceData <- "release"
	} else {
		data["ok"] = false
		data["err"] = "Permission Denied."
	}

	if err := json.NewEncoder(w).Encode(data); err != nil {
		panic(err)
	}
}

func RAReqCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	var data map[string]json.RawMessage
	requesterLC := &LamportClock{}
	requesterAccessClock := &LamportClock{}
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
	if err := json.Unmarshal(data["lc"], requesterLC); err != nil {
		panic(err)
	}
	if err := json.Unmarshal(data["accessClock"], requesterAccessClock); err != nil {
		panic(err)
	}

	//requesterLC.Counter = data["lc"]["counter"].(uint64)
	//requesterLC.ID = data["lc"]["id"].(int)
	//requesterAccessClock.Counter = data["accessClock"]["counter"].(uint64)
	//requesterAccessClock.ID = data["accessClock"]["id"].(int)
	//lc.Set(uint64(math.Max(float64(lc.Counter), float64(requesterLC.Counter))) + 1)
	lc.Recv(requesterLC.Counter)

	if accessStatus == 2 {
		accessCond.L.Lock()
		accessCond.Wait()
	} else if accessStatus == 1 {
		if accessClock.Compare(requesterAccessClock) == 1 {
			accessCond.L.Lock()
			accessCond.Wait()
		}
	}

	resData := map[string]interface{}{"ok": true, "err": "", "lc": lc}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	if err := json.NewEncoder(w).Encode(resData); err != nil {
		panic(err)
	}
}
