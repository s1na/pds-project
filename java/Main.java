
import org.apache.commons.cli.Options;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.ParseException;

import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.Scanner;

class Node {
    String ip = "100";
}

public class Main {

    /* JSON request over HTTP */
    //public static void requestJSONHttp(String address) throws Exception {
    public static void requestJSONHttp(String address) {

        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httppost = new HttpPost(address);
        httppost.setHeader("Content-Type", "application/json");

        Gson gson = new GsonBuilder().create();
        Node myNode = new Node();

        httppost.setEntity(new StringEntity(gson.toJson(myNode), "UTF-8"));

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
