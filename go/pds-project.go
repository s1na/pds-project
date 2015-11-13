package main

import (
	"fmt"
	"log"
	"net/http"
	"os"

	"github.com/codegangsta/cli"
	"github.com/julienschmidt/httprouter"
)

var serverPort string

func JoinCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	fmt.Fprint(w, "join")
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

		end := false
		for !end {
			var cmd string
			var arg1 string

			fmt.Print(">> ")
			fmt.Scanln(&cmd, &arg1)

			switch cmd {
			case "exit":
				end = true
			case "join":
				fmt.Println("Join")
			}
		}
	}

	app.Run(os.Args)
}

func server() {
	router := httprouter.New()
	router.GET("/join", JoinCtrl)
	router.GET("/signoff", SignOffCtrl)

	serverPort = fmt.Sprint(":", serverPort)
	log.Fatal(http.ListenAndServe(":8080", router))
}

func main() {
	client()
}
