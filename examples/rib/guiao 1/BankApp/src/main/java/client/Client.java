package client;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {
    public static void main(String[] args) {
        try{
            int myport = Integer.parseInt(args[0]);

            ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
            NettyMessagingService ms = new NettyMessagingService("synchronous", Address.from(myport), new MessagingConfig());

            ms.registerHandler("bank_message_movement", (a,m)->{
                System.out.println("> Bank response(" + a.port() + "): " + new String(m));
            }, es);

            ms.registerHandler("bank_message_balance", (a,m)->{
                System.out.println("> Bank response(" + a.port() + "): " + new String(m));
            }, es);

            ms.start();

            System.out.println(" > Bank Account Options");
            System.out.println("    balance <- returns balance of account");
            System.out.println("    movement AMOUNT <- deposits or withdraws the AMOUNT");

            int counter = 0;

            while (true){
                Scanner in = new Scanner(System.in);
                String s = in.nextLine();
                ms.sendAsync(Address.from("localhost", 12341), "client_message", s.getBytes());
                ms.sendAsync(Address.from("localhost", 12342), "client_message", s.getBytes());
                ms.sendAsync(Address.from("localhost", 12343), "client_message", s.getBytes());

            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
