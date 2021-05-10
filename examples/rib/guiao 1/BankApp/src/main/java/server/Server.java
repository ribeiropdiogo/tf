package server;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Server {

    public static void main(String[] args) throws IOException {
        int myport = Integer.parseInt(args[0]);

        BankAccount bank = new BankAccount();

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

        while (true){
            Scanner in = new Scanner(System.in);
            String s = in.nextLine();
        }
    }
}
