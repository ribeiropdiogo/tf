package server;

import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.messaging.impl.NettyMessagingService;
import io.atomix.utils.net.Address;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import spread.*;

public class Server {

    static LinkedList<String> fifo = new LinkedList<String>();
    static LinkedList<String> unknown = new LinkedList<String>();
    static String[] members = {"s1","s2","s3"};
    static String role = "backup";

    public static void main(String[] args) throws IOException, SpreadException {
        int myport = Integer.parseInt(args[0]);
        String name = "s" + Integer.parseInt(args[1]);
        final int[] state = {1};

        for (String m : members)
            unknown.add(m);

        Queue<SpreadMessage> queue = new LinkedList();

        BankAccount bank = new BankAccount();
        SpreadConnection connection = new SpreadConnection();
        connection.connect(InetAddress.getByName("localhost"), myport, name, false, true);

        SpreadGroup group = new SpreadGroup();
        group.join(connection, "bank");

        connection.add(new AdvancedMessageListener() {
            @Override
            public void regularMessageReceived(SpreadMessage msg) {
                if (role == "backup"){
                    System.out.println("> New Update");
                    String[] split = new String(msg.getData()).split(" ");
                    bank.setBalance(Double.parseDouble(split[1]));
                    System.out.println("> State Transfer Complete");
                    System.out.println("> Balance: " + bank.balance());
                } else if (role == "leader"){
                    System.out.println("> Updates Delivered");
                }
            }

            @Override
            public void membershipMessageReceived(SpreadMessage msg){
                MembershipInfo info = msg.getMembershipInfo();
                String s;

                if (info.isCausedByJoin()){
                    s = info.getJoined().toString();
                    s = s.substring(1,3);
                    fifo.add(s);
                    unknown.remove(s);
                    //System.out.println(fifo);
                    //System.out.println(unknown);
                    if (fifo.get(0).equals(name) && unknown.size() == 0){
                        System.out.println("> " + "I'm the leader");
                        role = "leader";
                    } else {
                        //System.out.println("> " + "I'm a backup server");
                        role = "backup";
                    }
                } else {
                    s = info.getLeft().toString();
                    s = s.substring(1,3);
                    fifo.remove(s);
                    unknown.remove(s);
                    //System.out.println(fifo);
                    //System.out.println(unknown);

                    if (fifo.get(0).equals(name) && unknown.size() == 0){
                        System.out.println("> " + "I'm the leader");
                        role = "leader";
                    } else {
                        System.out.println("> " + "I'm a backup server");
                        role = "backup";
                    }
                }
            }
        });


        ServerSocket ss = new ServerSocket(Integer.parseInt(args[2]));

        while (true) {
            Socket s = ss.accept();

            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String data = br.readLine();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));

                while (data != null && !data.equals("exit")) {
                    //System.out.println("> Received " + data);
                    String[] split = data.split(" ");

                    if (split[0].equals("balance")) {
                        double r = bank.balance();
                        pw.println(r);
                        pw.flush();
                    } else if (split[0].equals("movement")) {
                        double amount = Double.parseDouble(split[1]);
                        boolean r = bank.movement(amount);

                        SpreadMessage message = new SpreadMessage();
                        String update_state = "state " + bank.balance() + " " + r;
                        message.setData(update_state.getBytes());
                        message.setSafe();
                        message.addGroup("bank");

                        try {
                            connection.multicast(message);
                            pw.println(r);
                            pw.flush();
                        } catch (SpreadException e) {
                            e.printStackTrace();
                        }
                    }
                    data = br.readLine();
                }
        }

    }
}
