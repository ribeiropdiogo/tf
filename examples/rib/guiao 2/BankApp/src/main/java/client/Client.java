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
            int myport = Integer.parseInt(args[0]);

            SpreadConnection conn = new SpreadConnection();
            conn.connect(InetAddress.getByName("localhost"), myport, "client_"+myport, false, false);

            SpreadGroup group = new SpreadGroup();
            group.join(conn, "client");
            /*
            ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
            NettyMessagingService ms = new NettyMessagingService("synchronous", Address.from(myport), new MessagingConfig());

            ms.registerHandler("bank_message_movement", (a,m)->{
                System.out.println("> Bank response(" + a.port() + "): " + new String(m));
            }, es);

            ms.registerHandler("bank_message_balance", (a,m)->{
                System.out.println("> Bank response(" + a.port() + "): " + new String(m));
            }, es);

            ms.start();
            */

            conn.add(new BasicMessageListener() { @Override
                public void messageReceived(SpreadMessage msg) {
                    System.out.println("> Bank response: " + new String(msg.getData()));
                }
            });



            System.out.println(" > Bank Account Options");
            System.out.println("    balance <- returns balance of account");
            System.out.println("    movement AMOUNT <- deposits or withdraws the AMOUNT");

            int counter = 0;

            while (true){
                Scanner in = new Scanner(System.in);
                String s = in.nextLine();
                SpreadMessage message = new SpreadMessage();
                message.setData(s.getBytes());
                message.setSafe();
                message.addGroup("bank");
                conn.multicast(message);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
