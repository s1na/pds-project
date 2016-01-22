package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"net/http"
)

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

func syncCentralizedReq(dest string) []byte {
	resp, err := http.Get(fmt.Sprint(dest, "/sync/centralized/req"))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}

func syncCentralizedRelease(dest string) []byte {
	resp, err := http.Get(fmt.Sprint(dest, "/sync/centralized/release"))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}

func syncRAReq(dest string) []byte {
	lc.Increment()
	data := map[string]interface{}{"lc": *lc, "accessClock": *accessClock}
	msg, _ := json.Marshal(data)
	resp, err := http.Post(fmt.Sprint(dest, "/sync/ra/req"), "application/json", bytes.NewBuffer(msg))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	var resData map[string]json.RawMessage
	if err := json.Unmarshal(resBody, &resData); err != nil {
		panic(err)
	}
	var ok bool
	var errs string
	rLC := &LamportClock{}
	json.Unmarshal(resData["ok"], &ok)
	json.Unmarshal(resData["err"], &errs)
	json.Unmarshal(resData["lc"], rLC)
	if !ok {
		panic(errs)
	}
	lc.Recv(rLC.Counter)
	return resBody
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

func finishReq(dest string) []byte {
	resp, err := http.Get(fmt.Sprint(dest, "/finish"))
	if err != nil {
		panic(err)
	}
	resBody, _ := ioutil.ReadAll(resp.Body)
	return resBody
}
