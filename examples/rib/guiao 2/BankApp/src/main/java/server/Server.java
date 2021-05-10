package server;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import spread.*;

public class Server {

    public static void main(String[] args) throws IOException, SpreadException {
        int myport = Integer.parseInt(args[0]);

        BankAccount bank = new BankAccount();
        SpreadConnection connection = new SpreadConnection();
        connection.connect(InetAddress.getByName("localhost"), myport, "bank_"+myport, false, false);

        SpreadGroup group = new SpreadGroup();
        group.join(connection, "bank");

        /*
        ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
        NettyMessagingService ms = new NettyMessagingService("synchronous", Address.from(myport), new MessagingConfig());

        ms.registerHandler("client_message", (a,m)->{
            String[] split = new String(m).split(" ");

            if (split[0].equals("balance")){
                double r = bank.balance();
                String response = String.valueOf(r);
                ms.sendAsync(Address.from("localhost", a.port()), "bank_message_balance", response.getBytes());
            } else if (split[0].equals("movement")){
                double amount = Double.parseDouble(split[1]);
                //System.out.println("> movement " + amount);
                boolean r = bank.movement(amount);
                String response = String.valueOf(r);
                ms.sendAsync(Address.from("localhost", a.port()), "bank_message_movement", response.getBytes());

            }
        }, es);

        ms.start();
        */

        connection.add(new BasicMessageListener() { @Override
            public void messageReceived(SpreadMessage msg) {
                String[] split = new String(msg.getData()).split(" ");
                String response = "";
    
                if (split[0].equals("balance")){
                    double r = bank.balance();
                    response = "balance " + r;
                    System.out.println(response);
                } else if (split[0].equals("movement")){
                    double amount = Double.parseDouble(split[1]);
                    //System.out.println("> movement " + amount);
                    boolean r = bank.movement(amount);
                    response = "movement " + r;
                }


                SpreadMessage message = new SpreadMessage();
                message.setData(response.getBytes());
                message.setSafe();
                message.addGroup("client");
    
                try {
                    connection.multicast(message);
                } catch (SpreadException e) {
                    e.printStackTrace();
                }
            }
        });

        while (true){
            Scanner in = new Scanner(System.in);
            String s = in.nextLine();
        }

    }
}
