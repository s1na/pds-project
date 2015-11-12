package main

import (
	"fmt"
	"log"
	"net/http"

	"github.com/julienschmidt/httprouter"
)

func JoinCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	fmt.Fprint(w, "join")
}

func SignOffCtrl(w http.ResponseWriter, r *http.Request, _ httprouter.Params) {
	fmt.Fprint(w, "signoff")
}

func main() {
	router := httprouter.New()
	router.GET("/join", JoinCtrl)
	router.GET("/signoff", SignOffCtrl)

	log.Fatal(http.ListenAndServe(":8080", router))
}
