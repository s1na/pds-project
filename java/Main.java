
import org.apache.commons.cli.Options;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;

import com.sun.net.httpserver.*;

import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.util.Scanner;
import java.util.ArrayList;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;

import java.net.HttpURLConnection;
import java.net.URL;

public class Main {

    public static ArrayList<Node> network = new ArrayList<Node>();

    /* HttpHandlers */
    static class JoinCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            InputStream requestBody = exchange.getRequestBody();
            Reader reader = new InputStreamReader(requestBody, "UTF-8");
            Node node = new Gson().fromJson(reader, Node.class);

            /* add node to the network */
            network.add(node);

            /* Send notification of update to the rest of the nodes */
            for (Node net: network) {
                System.out.println(net.getID());
                System.out.println(net.getAddress());
            }

            /*
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
            */

        }
    }

    /* JSON request over HTTP 
    public static void requestJSONHttp(String address) {

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(address);
        httppost.setHeader("Content-Type", "application/json");

        Gson gson = new GsonBuilder().create();
        Node myNode = new Node(0, "", false);

        httppost.setEntity(new StringEntity(gson.toJson(myNode), "UTF-8"));

    }
    */

    public static void requestJSONHttp(String address) {

        String json = "{\"addr\": \"http://192.168.71.43:8080\"}";

        try {


            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            //con.setRequestProperty("Accept", "application/json");
            
            OutputStreamWriter writer = new OutputStreamWriter(conn.getOutputStream());
            writer.write(json);
            writer.close();


            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                System.out.println("OK");
            } else {
                System.out.println("Not OK");
            }

        } catch (IOException ex) {
            System.err.println(ex);
        }

    }

    public static void main(String[] args) {

        int port = 8080;
        boolean alive = true;

        Options options = new Options();
        options.addOption("p", "port", true, "Port");
        CommandLineParser parser = new DefaultParser();

        try {

            CommandLine line = parser.parse(options, args);
            if ( line.hasOption( "port" ) ) {
                port = Integer.parseInt( line.getOptionValue("port") );
            }

        } catch (ParseException exp) {
            System.err.println("Parsing failed. Reason " + exp.getMessage() );
        }


        try {

            HttpServer server = HttpServer.create(new InetSocketAddress(port) ,0);
            server.createContext("/join", new JoinCtrl() );
            server.setExecutor(null);
            server.start();
            //System.out.println(server.getAddress());

        } catch (IOException ex) {
            System.out.print(ex);
        }

        while(alive) {
            System.out.print(">>> ");
            Scanner in = new Scanner(System.in);
            String s = in.nextLine();
            // ToDo: create exceptions
            
            if ( s.equals("join") ) {
                //ToDo
                requestJSONHttp("http://192.168.71.43:8081/join");
                
            } else if ( s.equals("exit") || s.equals("signoff") ) {
                //ToDo
                //leave the group, update the network
                alive = false;
                //leave();
            } else if ( s.equals("list") ) {
                System.out.println("ToDo: List");
            } else if ( s.equals("start") ) {
                System.out.println("ToDo: Start");
            } else if ( s.equals("42") ) {
                System.out.println("Die Antwort nach dem Leben, dem " +  
                                   "Universum und allem");
            } else if ( !s.equals("") ) {
                System.out.println("Unknown command");
            }
        }
    }
}
