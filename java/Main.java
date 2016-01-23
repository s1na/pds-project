
import org.apache.commons.cli.Options;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import com.sun.net.httpserver.*;

import java.net.NetworkInterface;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.BindException;
import java.net.UnknownHostException;

import java.util.Scanner;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.io.Reader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.lang.reflect.Type;


public class Main {

    public static ArrayList<Node> network = new ArrayList<Node>();
    public static int port = 8080;
    public static String address= ""; //"http://192.168.71.43";
    public static boolean alive = true;
    public static HttpServer server = null;

    static String convertStreamToString(java.io.InputStream is) {
            java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
                return s.hasNext() ? s.next() : "";
    }

    private static InetAddress getInetAddress() {

        InetAddress ia = null;

        try {

            /*
            Enumeration e = NetworkInterface.getNetworkInterfaces();

            while(e.hasMoreElements())
            {
                NetworkInterface n = (NetworkInterface) e.nextElement();
                Enumeration ee = n.getInetAddresses();

                while (ee.hasMoreElements())
                {
                    InetAddress i = (InetAddress) ee.nextElement();
                    System.out.println(i.getHostAddress());
                }
            }
            */

            //NetworkInterface n = NetworkInterface.getByName("wlan0");
            NetworkInterface n = NetworkInterface.getByName("eth0");
            Enumeration ee = n.getInetAddresses();

            while (ee.hasMoreElements()) {
                ia = (InetAddress) ee.nextElement();
            }

        } catch (Exception e) {
            System.err.println(e);
            System.err.println("Port " + port + " is already in use" );
            System.exit(0);
        }

        return ia;
    }

    /* Auxiliary functions */
    private static String getString(InputStream is) {

        BufferedReader br = null;
        StringBuilder sb = new StringBuilder();
        String line;

        try {

            br = new BufferedReader(new InputStreamReader(is));
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return sb.toString();
    }

    /* List known nodes */
    private static void listNodes() {
        for (Node net: network) {
            
            System.out.println();
            System.out.println("ID -> "  + net.getID());
            System.out.println("  Address -> " + net.getAddress());
            System.out.println("  Master -> " + net.isMaster());

        }
    }

    /* HttpHandlers */
    /* JoinCtrl */
    private static class JoinCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            InputStream requestBody = exchange.getRequestBody();
            //System.out.println("->  " + getString(requestBody)); 
            Reader reader = new InputStreamReader(requestBody, "UTF-8");
            Node node = new Gson().fromJson(reader, Node.class);

            System.out.println("Request from " + node.getAddress() );

            int _id = network.get(network.size() - 1).getID();
            node.setID(_id + 1);

            /* add node to the network */
            /* avoid repetition ??? */
            network.add(node);

            /* Send notifications of update to the rest of the nodes, except
             * the sender and the receiver.
             * */

            for (Node net: network) {

                if (!net.getAddress().equals(node.getAddress()) && 
                    !net.getAddress().equals(network.get(0).getAddress())) {

                    System.out.println("Sending update for " + net.getAddress());
                    networkUpdateReq(net.getAddress());

                }
                //System.out.println(net.getID());
                //System.out.println(net.getAddress());
            }

            String response = new Gson().toJson(network);
            exchange.sendResponseHeaders(200, response.length());

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.flush();

            exchange.close();

        }
    }

    /* NetworkUpdateCtrl */
    /* Receive a network structur and update the current network */
    private static class NetworkUpdateCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            InputStream is = exchange.getRequestBody();
            //System.out.println("->  " + getString(requestBody)); 
            Gson gson = new Gson();
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            Type listType = new TypeToken<ArrayList<Node>>() { }.getType();
            ArrayList<Node> net = gson.fromJson(reader, listType);

            //update the network with the information received
            network = net;

            exchange.sendResponseHeaders(200, 0);
            exchange.close();

        }
    }


    /* Request operations */

    public static void joinReq(String address) {

        /* get first node */
        String json = "{\"addr\": \"" + network.get(0).getAddress() + "\"}";

        try {

            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", 
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            
            OutputStreamWriter writer = new OutputStreamWriter(
                    conn.getOutputStream()
                    );
            writer.write(json);
            writer.close();

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                System.out.println("OK");
                InputStream is = conn.getInputStream();
                //System.out.println(convertStreamToString(is));
                Gson gson = new Gson();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));

                Type listType = new TypeToken<ArrayList<Node>>() { }.getType();
                ArrayList<Node> net = gson.fromJson(reader, listType);

                //update the network with the information received
                network = net;

            } else {
                System.out.println("No response code");
            }


        } catch (IOException ex) {
            System.err.println(ex);
        }

    }

    /* Send the network structure to other node */
    /* Different logic implementation in Go */
    public static void networkUpdateReq(String address) {

        String json = new Gson().toJson(network);

        try {

            URL url = new URL(address);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");
            
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

        /* Server Side */
        try {

            InetAddress ia = getInetAddress();
            server = HttpServer.create(new InetSocketAddress(ia, port) ,0);

            server.createContext("/join", new JoinCtrl() );
            server.createContext("/network/update", new NetworkUpdateCtrl() );

            /* Listener */
            server.setExecutor(Executors.newFixedThreadPool(20));
            //server.setExecutor(Executors.newCachedThreadPool());

            server.start();

            address = server.getAddress().toString();
            address = "http://" + address.substring(1, address.length());
            network.add(new Node(0, address, false));

        } catch (BindException ex) {
            System.err.println("Port " + port + " is already in use" );
            System.exit(0);
        } catch (IOException ex) {
            System.err.println(ex);
            System.exit(0);
        }

        while(alive) {

            System.out.print(">>> ");
            Scanner in = new Scanner(System.in);
            String line = in.nextLine();

            String[] s = line.split("\\s");
            
            if ( s[0].equals("join") ) {

                joinReq(s[1] + "/join");
                
            } else if ( s[0].equals("exit") || s[0].equals("signoff") ) {
                //ToDo
                //leave the group, update the network
                alive = false;
                //leave();
                server.stop(0);

            } else if ( s[0].equals("list") ) {
                listNodes();
            } else if ( s[0].equals("start") ) {
                System.out.println("ToDo: Start");
            } else if ( s[0].equals("42") ) {
                System.out.println("Die Antwort nach dem Leben, dem " +  
                                   "Universum und allem");
            } else if ( !s[0].equals("") ) {
                System.out.println("Unknown command");
            }
        }
    }
}
