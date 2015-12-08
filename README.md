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

### Run the code

- `go build`

