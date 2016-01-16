
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.*;
import java.io.*;

class Node {

}

public class ServerSide {

    private ServerSocket socket;
    private int port;

    public ServerSide(int port) {
        this.port = port;
    }

    /* start */
    public void start() throws IOException {

        socket = new ServerSocket(port);
        Socket client = socket.accept();
        message(client);

    }

    /* private */
    private void message(Socket client) throws IOException {

        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));
        writer.write("Successful connection");
        writer.flush();
        writer.close();
    }

}
