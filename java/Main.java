
import java.net.BindException;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {

        // Setting a default port number.
        int portNumber = Integer.parseInt(args[0]);

        try {
            // initializing the Socket Server
            ServerSide socketServer = new ServerSide(portNumber);
            socketServer.start();

        } catch (BindException e) {
            System.err.println("Address " + portNumber +  " already in use");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
