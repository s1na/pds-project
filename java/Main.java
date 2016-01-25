
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
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.io.Reader;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.BufferedReader;
import java.lang.reflect.Type;
import java.lang.*;

/* */
class JSONResponse {

    public boolean ok;
    public String err;
    public String data;

    public JSONResponse(boolean ok, String err, String data) {
        this.ok = ok;
        this.err = err;
        this.data = data;
    }
}

public class Main {

    public static Node masterNode = null;
    public static Node selfNode = null;
    public static ArrayList<Node> network = new ArrayList<Node>();
    public static String localAddress= "";
    public static boolean alive = true;
    public static HttpServer server = null;
    public static boolean startedDRW = false;

    public static String masterResCtrl = "";
    public static String masterResourceData = "";
    public static String masterCurNode = "";

    /* Default values for the parameters */
    public static String syncAlgorithm = "centralized";
    public static String networkInterface = "eth0";
    public static int port = 8080;

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
            NetworkInterface n = NetworkInterface.getByName(networkInterface);
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
                    System.out.println("Setting coordinator " + data.getID());
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
            if (syncAlgorithm != "centralized" || !masterNode.equals(selfNode)
                && !startedDRW) {
                distributedRW();
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

    /*syncCentralizedreq controller*/
    private static class CentralizedReqCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException{
            if(masterResCtrl == ""){
                masterCurNode = masterNode.getAddress();
                masterResCtrl = "lock";
                exchange.sendResponseHeaders(200, 0);
            } else{
                masterCurNode = "";
                masterResCtrl = "";
            }
            exchange.close();
        }
    }

    /*syncCentralizedRelease controller*/
    private static class CentralizedReleaseCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
        	masterCurNode = "";
    		masterResCtrl = "";
    		exchange.sendResponseHeaders(200, 0);
    		exchange.close();
        }
    }

    /* ReadCtrl */
    private static class ReadCtrl implements HttpHandler {

        JSONResponse data = new JSONResponse(true, "", "");

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if (syncAlgorithm == "centralized") {
                if ( exchange.getRemoteAddress().equals(masterCurNode) ) {
                    data.data = masterResourceData;
                } else {
                    data.ok = false;
                    data.err = "Permission Denied";
                }
            } else if (syncAlgorithm == "ra"){
                //data.data = masterSharedData;
            }

            String response = new Gson().toJson(data);
            exchange.sendResponseHeaders(200, response.length());

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.flush();

            exchange.close();

        }
    }

    /* FinishCtrl */
    private static class FinishCtrl implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {

            //masterFinishCond.L.Lock();
            //masterFinished++;
            int boundary = network.size() - 1;
            if (syncAlgorithm == "ra") {
                boundary = network.size() - 1;
            }
            /*
            if (masterFinished == boundary) {
                masterFinishedCond.Broadcast()
            } else {
                masterFinishedCond.Wait()
            }
            */
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
    public static String readDataReq(String address) {

        String answer = "";

        try {

            URL url = new URL(address + "/read");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Content-Type",
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                System.out.println("OK");
                InputStream is = conn.getInputStream();
                answer = convertStreamToString(is);

            } else {
                System.out.println("No response code");
                answer = "";
            }

        } catch (IOException ex) {
            System.err.println(ex);
        }

        return answer;
    }

    /* writeDataReq */
    /* receive as a parameter the master node */
    public static String writeDataReq(String address, String shData) {

        String json = "{\"data\": \"" + shData + "\"}";

        try {

            URL url = new URL(address + "/write");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {

                System.out.println("OK");
                InputStream is = conn.getInputStream();
                answer = convertStreamToString(is);

            } else {
                System.out.println("No response code");
                answer = "";
            }

        } catch (IOException ex) {
            System.err.println(ex);
        }

        return answer;
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

    /* finishReq */
    public static void finishReq(String address) {

        try {

            URL url = new URL(address + "/finish");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

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

    public static boolean syncCentralizedReq(){
        boolean response = false;
    	try{
    		URL url = new URL(masterNode.getAddress() + "/sync/centralized/req");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            if(conn.getResponseCode() == HttpURLConnection.HTTP_OK){
            	response = true;
            } else{
            	response = false;
            }
    	} catch (IOException ex) {
            System.err.println(ex);
        }
        return response;
    }

    public static boolean syncCentralizedRelease(){
        boolean response = false;
    	try{
    		URL url = new URL(masterNode.getAddress() + "/sync/centralized/release");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type",
                    "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            if(conn.getResponseCode() == HttpURLConnection.HTTP_OK){
            	response = true;
            } else{
            	response = false;
            }
    	} catch (IOException ex) {
            System.err.println(ex);
        }
        return response;
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

        if (won == true) {

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
    	if(syncAlgorithm == "centralized"){

    		boolean resBody = syncCentralizedReq();

            if (resBody == true){
            	//send read request
            	String data = readDataReq(masterNode.getAddress());
            	String nodeString = masterNode.getAddress() + " ";
            	data = data + nodeString;
            	writeDataReq(masterNode.getAddress(), data);
            } else {
                // return false;
            }
            resBody = syncCentralizedRelease();
            if (resBody == true){
            	System.out.println("Ok");
            }
    	}
    }

    /* distributedRW */
    /*
    public static void distributedRW() {

        startedDRW = true;
        ArrayList<String> addedWords = new ArrayList<String>();
        Date startingTime = Calendar.getInstance().getTime();
        System.out.println( startingTime );

        if (syncAlgorithm.equals("centralized")) {
            //syncCentralizedReq(masterNode.getAddress());
        }
    }
    */

    public static void main(String[] args) {

        Options options = new Options();
        options.addOption("p", "port", true, "Port");
        options.addOption("s", "sync", true, "Synchronization");
        options.addOption("i", "interface", true, "Interface");
        CommandLineParser parser = new DefaultParser();

        try {

            CommandLine line = parser.parse(options, args);
            if ( line.hasOption("port") ) {
                port = Integer.parseInt( line.getOptionValue("port") );
            }
            if ( line.hasOption("sync") ) {
                syncAlgorithm = line.getOptionValue("sync");
                if ( !syncAlgorithm.equals("centralized")
                        && !syncAlgorithm.equals("ra") ) {
                    System.out.println("Sync algorithm not supported, use"
                            + " centralized or ra (Ricard & Agrawala).");
                    System.exit(0);
                }
            }
            if ( line.hasOption("interface") ) {
                networkInterface = line.getOptionValue("interface");
            }
        } catch (ParseException exp) {
            System.err.println("Parsing failed. Reason " + exp.getMessage());
        }

        /* Server Side */
        try {

            InetAddress ia = getInetAddress();
            server = HttpServer.create(new InetSocketAddress(ia, port) ,0);

            /* GET */
            server.createContext("/election", new ElectionCtrl() );
            server.createContext("/start", new StartCtrl() );
            server.createContext("/finish", new FinishCtrl() );
            server.createContext("/read", new ReadCtrl() );
            server.createContext("/sync/centralized/req", new CentralizedReqCtrl());
            server.createContext("/sync/centralized/release", new CentralizedReleaseCtrl());

            /* POST */
            server.createContext("/coordinator", new CoordinatorCtrl() );
            server.createContext("/join", new JoinCtrl() );
            server.createContext("/network/update", new NetworkUpdateCtrl() );
            server.createContext("/write", new WriteCtrl() );
            //server.createContext("/sync/ra/req", new RaReqCrl() );

            /* Listener */
            server.setExecutor(Executors.newFixedThreadPool(20));
            //server.setExecutor(Executors.newCachedThreadPool());

            server.start();

            localAddress = server.getAddress().toString();
            localAddress = "http://" + localAddress.substring(1,
                    localAddress.length());
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

            String[] s = line.split("\\s+");

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
