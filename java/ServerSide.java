
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.*;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

class Node {

}

public class ServerSide {

    private ServerSocket socket;
    private int port;

    public ServerSide(int port) {
        this.port = port;
    }

    /* start */
    // public void start() throws IOException {
    public void start() throws IOException {

        socket = new ServerSocket(port);
        Socket client = socket.accept();
        message(client);

    }

    /* join */
    public void join() {
        //socketClient = new Socket(hostname, port);
    }

    /* message */
    private void message(Socket client) throws IOException {

        BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(client.getOutputStream())
                );
        writer.write("Successful connection");
        writer.flush();
        writer.close();
    }

}
