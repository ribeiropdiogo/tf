package server;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import spread.*;

public class Server {

    public static void replayMessage(BankAccount bank, SpreadMessage msg){
        String[] split = new String(msg.getData()).split(" ");

        if (split[0].equals("balance")) {
            double r = bank.balance();
        } else if (split[0].equals("movement")) {
            double amount = Double.parseDouble(split[1]);
            boolean r = bank.movement(amount);
        }

    }

    public static void processMessage(BankAccount bank, SpreadConnection connection, SpreadMessage msg){
        String[] split = new String(msg.getData()).split(" ");
        String response = "";

        if (split[0].equals("balance")) {
            double r = bank.balance();
            response = "balance " + r;
            System.out.println(response);
        } else if (split[0].equals("movement")) {
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

    public static void main(String[] args) throws IOException, SpreadException {
        int myport = Integer.parseInt(args[0]);
        final int[] state = {1};
        Queue<SpreadMessage> queue = new LinkedList();

        BankAccount bank = new BankAccount();
        SpreadConnection connection = new SpreadConnection();
        connection.connect(InetAddress.getByName("localhost"), myport, "bank_"+myport, false, true);

        SpreadGroup group = new SpreadGroup();
        group.join(connection, "bank");

        connection.add(new AdvancedMessageListener() {
            @Override
            public void regularMessageReceived(SpreadMessage msg) {
                String[] split = new String(msg.getData()).split(" ");
                String response = "";

                if (state[0] < 4) {
                    if (split[0].equals("state")) {
                        bank.setBalance(Double.parseDouble(split[1]));
                        state[0] = 3;
                        while (queue.size() > 0)
                            replayMessage(bank,queue.remove());
                        state[0] = 4;
                        System.out.println("> State Transfer Complete");
                    } else {
                        queue.add(msg);
                    }
                } else
                    processMessage(bank,connection,msg);
            }

            @Override
            public void membershipMessageReceived(SpreadMessage msg){
                MembershipInfo info = msg.getMembershipInfo();
                if (info.isRegularMembership()){
                    System.out.println("> New server joined");

                    SpreadMessage message = new SpreadMessage();
                    String state = "state " + bank.balance();
                    message.setData(state.getBytes());
                    message.setSafe();
                    message.addGroup(info.getGroup());

                    try {
                        connection.multicast(message);
                    } catch (SpreadException e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        while (true){
            Scanner in = new Scanner(System.in);
            String s = in.nextLine();
        }

    }
}
