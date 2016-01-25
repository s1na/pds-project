
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

    public static Node masterNode = null;
    public static Node selfNode = null;
    public static ArrayList<Node> network = new ArrayList<Node>();
    public static int port = 8080;
    public static String localAddress= ""; //"http://192.168.71.43";
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

    /* CoordinatorCtrl */
    private static class CoordinatorCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            InputStream requestBody = exchange.getRequestBody();
            //System.out.println("->  " + getString(requestBody));
            Reader reader = new InputStreamReader(requestBody, "UTF-8");
            Node data = new Gson().fromJson(reader, Node.class);

            for (Node n: network) {
                if (n.getAddress().equals(data.getAddress())) {
                    System.out.println("Setting coordinator " + data);
                    network.get(network.indexOf(n)).setMaster(true) ;
                    masterNode = network.get(network.indexOf(n));
                }
            }

            exchange.close();
        }
    }

    /* ElectionCtrl */
    private static class ElectionCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            startElection();
        }
    }

    /* NetworkUpdateCtrl */
    /* Receive a network structure and update the current network */
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

    /* StartCtrl */
    private static class StartCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            System.out.println("Start distributed read and write");

            /*
            if (syncAlgorithm != "centralized" || masterNode != selfNode) 
                && !startedDRW {
                distributedRW()
            }
            */

            exchange.sendResponseHeaders(200, 0);
            exchange.close();

        }
    }

    /* WriteCtrl */
    private static class WriteCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

        }
    }

    /* ReadCtrl */
    private static class ReadCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

        }
    }


    /* Request operations */
    /* joinReq */
    public static void joinReq(String address) {

        String json = "{\"addr\": \"" + localAddress + "\"}";

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

    /* readDataReq */
    /* receive as a parameter the master node */
    public static void readDataReq(String address) {

        String json = "";

        try {

            URL url = new URL(address + "/read");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type",
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStreamWriter writer = new OutputStreamWriter(
                    conn.getOutputStream());
            writer.write(json);
            writer.close();

            /* remove node */

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                System.out.println("OK");
                InputStream is = conn.getInputStream();
                //System.out.println(convertStreamToString(is));
                Gson gson = new Gson();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is));

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
            URL url = new URL(address + "/network/update");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", 
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStreamWriter writer = new OutputStreamWriter(
                    conn.getOutputStream());
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
    
    /* coordinatorReq */
    public static void coordinatorReq(String address) {

        String json = "{\"addr\": \"" + localAddress + "\"}";

        try {

            URL url = new URL(address + "/coordinator");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", 
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            OutputStreamWriter writer = new OutputStreamWriter(
                    conn.getOutputStream());
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

    /* startElection */
    public static void startElection() {

    	boolean won = true;
        for (Node n: network) {

            if ( n.getID() > selfNode.getID() ) {
                System.out.println("Sending election for " + n.getID());
                /* */
                try {

                    URL url = new URL(n.getAddress() + "/election");
                    HttpURLConnection conn = 
                        (HttpURLConnection) url.openConnection();

                    conn.setDoOutput(true);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-Type", 
                            "application/json; charset=UTF-8");
                    conn.setRequestProperty("Accept", "application/json");

                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        System.out.println("OK");
                        won = false;
                        break; // Why this break? AAA
                    } else {
                        System.out.println("Not OK");
                    }

                } catch (IOException ex) {
                    System.err.println(ex);
                }
            }
        }

        if (won) {
            for (Node n: network) {
                if ( n.getAddress().equals(localAddress) ) {
                    System.out.println("Setting self as master");
                    network.get( network.indexOf(n) ).setMaster(true) ;
                    masterNode = network.get(network.indexOf(n));

                    /*
                    masterFinishCond = sync.NewCond(&sync.Mutex{})
                    masterFinished = 0
                    if syncAlgorithm == "centralized" {
                        masterResourceControl = make(chan string)
                        masterResourceData = make(chan string)
                        masterCurNode = ""
                        go centralizedResourceManager()
                    }
                    if shData != "" {
                        masterSharedData = shData
                    }
                    */
                } else {
                    System.out.println("Sending coordinator for " 
                            + n.getAddress());
                    coordinatorReq(n.getAddress());
                }
            }

            broadcastStart();
            // if syncAlgorithm != "centralized" || masterNode != selfNode {
            //     distributedRW()
            // }
        }
    }

    /* broadcastStart */
    public static void broadcastStart() {
        for (Node n: network) {
            if (!n.getAddress().equals(localAddress)) {
            //here Threads??? AAA
                try {
                    URL url = new URL(n.getAddress() + "/start");
                    HttpURLConnection conn = 
                        (HttpURLConnection) url.openConnection();

                    conn.setDoOutput(true);
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Content-Type", 
                            "application/json; charset=UTF-8");
                    conn.setRequestProperty("Accept", "application/json");

                    if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        System.out.println("OK");
                    } else {
                        System.out.println("Not OK");
                    }

                } catch (IOException ex) {
                    System.err.println(ex);
                }
            }
        }
    }

    /* distributedRW */
    public static void distributedRW() {
    }

    public static void main(String[] args) {

        Options options = new Options();
        options.addOption("p", "port", true, "Port");
        CommandLineParser parser = new DefaultParser();

        try {

            CommandLine line = parser.parse(options, args);
            if ( line.hasOption("port") ) {
                port = Integer.parseInt( line.getOptionValue("port") );
            }

        } catch (ParseException exp) {
            System.err.println("Parsing failed. Reason " + exp.getMessage() );
        }

        /* Server Side */
        try {

            InetAddress ia = getInetAddress();
            server = HttpServer.create(new InetSocketAddress(ia, port) ,0);

            /* GET */
            server.createContext("/election", new ElectionCtrl() );
            server.createContext("/start", new StartCtrl() );
            server.createContext("/read", new ReadCtrl() );

            /* POST */
            server.createContext("/join", new JoinCtrl() );
            server.createContext("/network/update", new NetworkUpdateCtrl() );
            server.createContext("/coordinator", new CoordinatorCtrl() );
            //server.createContext("/write", new WriteCtrl() );

            /* Listener */
            server.setExecutor(Executors.newFixedThreadPool(20));
            //server.setExecutor(Executors.newCachedThreadPool());

            server.start();

            localAddress = server.getAddress().toString();
            localAddress = "http://" + localAddress.substring(1, localAddress.length());
            network.add(new Node(0, localAddress, false));
            selfNode = new Node(0, localAddress, false);

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
                // update here selfNode AAAA
                selfNode = network.get(network.size()-1);

            } else if ( s[0].equals("exit") || s[0].equals("signoff") ) {

                for (Node n: network) {
                    if ( n.getAddress().equals(localAddress) ) {
                        network.remove( network.indexOf(n) );
                        break;
                    }
                }

                for (Node n: network) {
                    //System.out.println("-> " + n.getAddress());
                    networkUpdateReq(n.getAddress());
                }

                network.clear();
                network.add(new Node(0, localAddress, false));

                if ( s[0].equals("exit") ){
                    alive = false;
                    server.stop(0);
                }

            } else if ( s[0].equals("list") ) {
                listNodes();
            } else if ( s[0].equals("start") ) {
                startElection();
            } else if ( s[0].equals("42") ) {
                System.out.println("Die Antwort nach dem Leben, dem " +
                                   "Universum und allem");
            } else if ( !s[0].equals("") ) {
                System.out.println("Unknown command");
            }
        }
    }
}
