# Principles of Distributed Systems Project

Principles of Distributed Systems. Bonn University.

Command line tool that does distributed and write operation. Implemented in Go
and Java. 

## Set Go environment for Ubuntu

### Install Go

- `sudo apt-get install golang`

### Set `$GOPATH` variable:

- `mkdir -p ~/.golang`
- Add this line to the `.bashrc` file:
    + `export GOPATH=$HOME/.golang`


- Install Github libraries:
	+ `go get github.com/codegangsta/cli`
	+ `go get github.com/julienschmidt/httprouter`

### Build the code

- `go build`

### Run the program:

- Execute `./go`
    + Without parameters by default it takes the port 8080: `./go`
    + It is possible to specify the port: `./go -p 8081`
