package main

import (
	"encoding/json"
	"fmt"
	"math/rand"
	"net/http"
	"os"
	"sync"
	"time"

	"github.com/Sirupsen/logrus"
	"github.com/codegangsta/cli"
	"github.com/julienschmidt/httprouter"
)

type Node struct {
	ID     int    `json:"id"`
	Addr   string `json:"addr"`
	Master bool   `json:"master"`
}

var log = logrus.New()
var serverPort string
var netInterface string
var syncAlgorithm string
var network []Node
var selfNode *Node
var startTime time.Time
var elapsedTime float64
var shData string
var startedDRW bool
var lc *LamportClock
var accessStatus int
var accessClock *LamportClock
var accessCond *sync.Cond
var masterNode *Node
var masterSharedData string
var masterResourceControl chan string
var masterResourceData chan string
var masterCurNode string
var masterFinishCond *sync.Cond
var masterFinished int

func client() {
	app := cli.NewApp()
	app.Name = "PDS Project"

	app.Flags = []cli.Flag{
		cli.StringFlag{
			Name:  "port, p",
			Value: "8080",
			Usage: "Port for incoming requests",
		},
		cli.StringFlag{
			Name:  "sync, s",
			Value: "centralized",
			Usage: "Synchronization algorithm used, either centralized or ra (Ricart & Agrawala).",
		},
		cli.StringFlag{
			Name:  "interface, i",
			Value: "eth0",
			Usage: "Network interface to use",
		},
	}

	app.Action = func(c *cli.Context) {
		syncAlgorithm = c.String("sync")
		if syncAlgorithm != "centralized" && syncAlgorithm != "ra" {
			log.Fatal("Sync algorithm not supported, use centralized or ra (Ricard & Agrawala).")
			return
		}
		netInterface = c.String("interface")
		serverPort = c.String("port")
		network = make([]Node, 1)
		network[0] = Node{ID: 0, Addr: getLocalAddr(), Master: false}
		selfNode = &network[0]

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
				selfNode = &network[len(network)-1]

			} else if cmd == "list" {
				fmt.Println(network)
			} else if cmd == "start" {
				startElection()

				// Send start msg
				//broadcastStart()

				//if syncAlgorithm != "centralized" || masterNode != selfNode {
				//distributedRW()
				//}
			} else if cmd != "" {
				fmt.Println("Unknown command")
			}
		}
	}

	app.Run(os.Args)
}

func server(ch chan string) {
	router := httprouter.New()
	router.GET("/election", ElectionCtrl)
	router.GET("/start", StartCtrl)
	router.GET("/finish", FinishCtrl)
	router.GET("/read", ReadCtrl)
	router.GET("/sync/centralized/req", CentralizedReqCtrl)
	router.GET("/sync/centralized/release", CentralizedReleaseCtrl)
	router.POST("/coordinator", CoordinatorCtrl)
	router.POST("/join", JoinCtrl)
	router.POST("/network/update", NetworkUpdateCtrl)
	router.POST("/write", WriteCtrl)
	router.POST("/sync/ra/req", RAReqCtrl)

	// After start

	port := fmt.Sprint(":", serverPort)
	log.Fatal(http.ListenAndServe(port, router))
}

func main() {
	log.Level = logrus.DebugLevel
	rand.Seed(time.Now().UnixNano())
	masterSharedData = ""
	elapsedTime = 0
	startedDRW = false
	log.Formatter.(*logrus.TextFormatter).FullTimestamp = true
	client()
}
