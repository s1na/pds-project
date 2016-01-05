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
	ID     int    `json:"id"`
	Addr   string `json:"addr"`
	Master bool   `json:"master"`
}

var serverPort string
var selfID int
var network []Node
var masterNode *Node

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
		network[0] = Node{ID: 0, Addr: getLocalAddr(), Master: false}
		selfID = 0

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
				selfID = network[len(network)-1].ID
				fmt.Println(network[0].Addr, network[1].Addr)
			} else if cmd == "list" {
				fmt.Println(network)
			} else if cmd == "start" {
				// Hold election
				won := true
				fmt.Println(selfID)
				for _, n := range network {
					if n.ID > selfID {
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

				// Send start msg
			}
		}
	}

	app.Run(os.Args)
}

func server(ch chan string) {
	router := httprouter.New()
	router.GET("/election", ElectionCtrl)
	router.POST("/coordinator", CoordinatorCtrl)
	router.POST("/join", JoinCtrl)
	router.POST("/network/update", NetworkUpdateCtrl)

	port := fmt.Sprint(":", serverPort)
	log.Fatal(http.ListenAndServe(port, router))
}

func main() {
	client()
}
