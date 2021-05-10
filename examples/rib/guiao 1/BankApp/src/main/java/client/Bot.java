package client;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class Bot {
    public static void main(String[] args) {
        try{
            int[] servers = new int[]{ 12341, 12342, 12343 };
            int myport = Integer.parseInt(args[0]);
            int movements = 30000, selected;

            ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
            NettyMessagingService ms = new NettyMessagingService("synchronous", Address.from(myport), new MessagingConfig());

            Random rand = new Random();
            double local_amount = 0.0;
            String command;
            HashMap<Integer, List> responses = new HashMap<>();

            for (int i = 0; i < servers.length; i++) {
                responses.put(servers[i],new ArrayList());
            }

            ms.registerHandler("bank_message_balance", (a,m)->{
                System.out.println("> Bank response(" + a.port() + "): " + new String(m));
            }, es);

            ms.registerHandler("bank_message_movement", (a,m)->{
                String response = new String(m);
                List aux = responses.get(a.port());
                aux.add(response);
                responses.put(a.port(),aux);
            }, es);

            ms.start();

            for (int i = 0; i < movements; i++){
                int amount = rand.nextInt(1000);
                int signal = rand.nextInt(2);


                if (signal == 1){
                    command = "movement -" + amount;
                    if (local_amount >= amount)
                        local_amount -= amount;
                } else {
                    command = "movement " + amount;
                    local_amount += amount;
                }

                //System.out.println(command);

                ms.sendAsync(Address.from("localhost", 12341), "client_message", command.getBytes());
                ms.sendAsync(Address.from("localhost", 12342), "client_message", command.getBytes());
                ms.sendAsync(Address.from("localhost", 12343), "client_message", command.getBytes());

                while (true){
                    if (responses.get(servers[0]).size() > i){
                        selected = servers[0];
                        break;
                    } else if (responses.get(servers[1]).size() > i){
                        selected = servers[1];
                        break;
                    } else if (responses.get(servers[2]).size() > i){
                        selected = servers[2];
                        break;
                    }
                }

                if (responses.get(selected).get(i) == "false")
                    break;
            }

            command = "balance";

            ms.sendAsync(Address.from("localhost", 12341), "client_message", command.getBytes());
            ms.sendAsync(Address.from("localhost", 12342), "client_message", command.getBytes());
            ms.sendAsync(Address.from("localhost", 12343), "client_message", command.getBytes());

            System.out.println("Bot says: " + local_amount);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
