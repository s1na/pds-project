
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.UnknownHostException;

public class ClientSide {

    private String hostname;
    private int port;
    Socket socketClient;

    public ClientSide(String hostname, int port){

        this.hostname = hostname;
        this.port = port;

    }

    public void connect() throws UnknownHostException, IOException{

        socketClient = new Socket(hostname, port);
        System.out.println("Connected to " + hostname + ":" + port);

    }

}
