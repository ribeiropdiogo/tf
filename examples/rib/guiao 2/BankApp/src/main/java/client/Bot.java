package client;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;
import spread.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
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
            int movements = 100000, selected;

            SpreadConnection conn = new SpreadConnection();
            conn.connect(InetAddress.getByName("localhost"), myport, "client_"+myport, false, false);

            SpreadGroup group = new SpreadGroup();
            group.join(conn, "client");

            /*
            ScheduledExecutorService es = Executors.newScheduledThreadPool(1);
            NettyMessagingService ms = new NettyMessagingService("synchronous", Address.from(myport), new MessagingConfig());
            */

            Random rand = new Random();
            double local_amount = 0.0;
            String command;
            HashMap<Integer, List> responses = new HashMap<>();

            for (int i = 0; i < servers.length; i++) {
                responses.put(servers[i],new ArrayList());
            }

            /*
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
            */

            conn.add(new BasicMessageListener() { @Override
            public void messageReceived(SpreadMessage msg) {
                System.out.println("> Bank response: " + new String(msg.getData()));
                //String response = new String(msg.getData());
                //List aux = responses.get(msg.getSender());
                //aux.add(response);
                //responses.put(msg.getSender(),aux);
            }
            });

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

                SpreadMessage message = new SpreadMessage();
                message.setData(command.getBytes());
                message.setSafe();
                message.addGroup("bank");

                try {
                    conn.multicast(message);
                } catch (SpreadException e) {
                    e.printStackTrace();
                }
                /*
                ms.sendAsync(Address.from("localhost", 12341), "client_message", command.getBytes());
                ms.sendAsync(Address.from("localhost", 12342), "client_message", command.getBytes());
                ms.sendAsync(Address.from("localhost", 12343), "client_message", command.getBytes());
                 */

            }

            command = "balance";

            SpreadMessage message = new SpreadMessage();
            message.setData(command.getBytes());
            message.setSafe();
            message.addGroup("bank");

            try {
                conn.multicast(message);
            } catch (SpreadException e) {
                e.printStackTrace();
            }
            /*
            ms.sendAsync(Address.from("localhost", 12341), "client_message", command.getBytes());
            ms.sendAsync(Address.from("localhost", 12342), "client_message", command.getBytes());
            ms.sendAsync(Address.from("localhost", 12343), "client_message", command.getBytes());
             */

            System.out.println("Bot says: " + local_amount);


            //group.leave();
            //conn.disconnect();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
