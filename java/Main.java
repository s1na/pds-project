
import java.net.BindException;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.util.Scanner;

import org.apache.commons.cli.Options;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;

import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.UnrecognizedOptionException;

public class Main {

    public static void main(String[] args) {

        int port;
        boolean alive = true;

        if (args.length == 0) {
            port = 8080;
        } else { 
            port = Integer.parseInt(args[0]);
        }

        //Options options = new Options();
        //options.addOption("j", true, "Join");
        //CommandLineParser parser = new DefaultParser();
        //String[] argss = new String[]{ "--block-size=10" };

        try {
            while(alive) {
                System.out.print(">>> ");
                Scanner in = new Scanner(System.in);
                String s = in.nextLine();
                // ToDo: create exceptions
                
                if ( s.equals("join") ) {
                    //ToDo
                    //join(address, port);
                    ServerSide socketServer = new ServerSide(port);
                    socketServer.start();
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
        } catch (IOException e) {
            System.err.print(e.getMessage());
        }
    }
}
