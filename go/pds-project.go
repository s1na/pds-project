package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net"
	"net/http"
	"os"

	"github.com/codegangsta/cli"
	"github.com/julienschmidt/httprouter"
)

type Node struct {
	Addr   string `json:"addr"`
	Master bool   `json:"master"`
}

var serverPort string
var network []Node

func JoinCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	ip, _, _ := net.SplitHostPort(r.RemoteAddr)
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
	fmt.Println("Join req from: ", ip, ":", data["port"].(string))

	newNode := Node{Addr: fmt.Sprint(ip, ":", data["port"].(string)), Master: false}
	network = append(network, newNode)

	//resD := map[string]string{"network": "test"}
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")
	if err := json.NewEncoder(w).Encode(network); err != nil {
		panic(err)
	}
}

func SignOffCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	fmt.Fprint(w, "signoff")
}

func client() {
	app := cli.NewApp()
	app.Name = "PDS Project"

	app.Flags = []cli.Flag{
		cli.StringFlag{
			Name:  "port, p",
			Value: "8080",
			Usage: "Port for incoming requests",
		},
	}

	app.Action = func(c *cli.Context) {
		serverPort = c.String("port")
		selfAddr := fmt.Sprint("localhost:", serverPort)
		network = make([]Node, 1)
		network[0] = Node{Addr: selfAddr, Master: false}

		ch := make(chan string)
		go server(ch)

		for {
			var cmd string
			var arg1 string

			fmt.Print(">> ")
			fmt.Scanln(&cmd, &arg1)

			if cmd == "exit" {
				break
			} else if cmd == "join" {
				if arg1 == "" {
					panic("No reference node given.")
				}
				endpoint := fmt.Sprint(arg1, "/", "join")
				data := map[string]string{"port": serverPort}
				msg, _ := json.Marshal(data)
				resp, err := http.Post(endpoint, "application/json", bytes.NewBuffer(msg))
				if err != nil {
					panic(err)
				}
				fmt.Println(resp.Body)
				resBody, _ := ioutil.ReadAll(resp.Body)
				//var resData map[string]interface{}
				network = nil // TODO GC?
				if err := json.Unmarshal(resBody, &network); err != nil {
					panic(err)
				}
				fmt.Println(network[0].Addr, network[1].Addr)
			}
		}
	}

	app.Run(os.Args)
}

func server(ch chan string) {
	router := httprouter.New()
	router.POST("/join", JoinCtrl)
	router.GET("/signoff", SignOffCtrl)

	port := fmt.Sprint(":", serverPort)
	log.Fatal(http.ListenAndServe(port, router))
}

func main() {
	client()
}
