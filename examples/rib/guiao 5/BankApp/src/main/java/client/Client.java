package client;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import spread.BasicMessageListener;
import spread.SpreadConnection;
import spread.SpreadGroup;
import spread.SpreadMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {
    public static void main(String[] args) {
        try{
            Socket s = new Socket("localhost",2341);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter pw = new PrintWriter(s.getOutputStream());
            String response;

            System.out.println(" > Bank Account Options");
            System.out.println("    balance <- returns balance of account");
            System.out.println("    movement AMOUNT <- deposits or withdraws the AMOUNT");

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            String command = console.readLine();

            while (command != null && !command.equals("exit")){
                pw.println(command);
                pw.flush();
                response = in.readLine();
                System.out.println("> Bank response: " + response);
                command = console.readLine();
            }

            s.close();

        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
