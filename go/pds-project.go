package main

import (
	"encoding/json"
	"fmt"
	"log"
	//"net"
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
		network = make([]Node, 1)
		network[0] = Node{Addr: getLocalAddr(), Master: false}

		ch := make(chan string)
		go server(ch)

		for {
			var cmd string
			var arg1 string

			fmt.Print(">> ")
			fmt.Scanln(&cmd, &arg1)

			if cmd == "exit" || cmd == "signoff" {
				for i, n := range network {
					if n.Addr == getLocalAddr() {
						network[i] = network[len(network)-1]
						network = network[:len(network)-1]
						break
					}
				}
				fmt.Println(network)
				for _, n := range network {
					networkUpdateReq(n.Addr, network)
				}
				network = nil
				network = append(network, Node{Addr: getLocalAddr(), Master: false})
				if cmd == "exit" {
					break
				}
			} else if cmd == "join" {
				if arg1 == "" {
					panic("No reference node given.")
				}
				resBody := joinReq(arg1, getLocalAddr())
				//var resData map[string]interface{}
				network = nil // TODO GC?
				if err := json.Unmarshal(resBody, &network); err != nil {
					panic(err)
				}
				fmt.Println(network[0].Addr, network[1].Addr)
			} else if cmd == "list" {
				fmt.Println(network)
			}
		}
	}

	app.Run(os.Args)
}

func server(ch chan string) {
	router := httprouter.New()
	router.POST("/join", JoinCtrl)
	router.POST("/network/update", NetworkUpdateCtrl)

	port := fmt.Sprint(":", serverPort)
	log.Fatal(http.ListenAndServe(port, router))
}

func main() {
	client()
}
