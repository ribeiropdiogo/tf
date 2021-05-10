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
        HashMap<Integer,BankAccount> accounts = new HashMap();
        SpreadConnection connection = new SpreadConnection();
        connection.connect(InetAddress.getByName("localhost"), myport, name, false, true);

        SpreadGroup group = new SpreadGroup();
        group.join(connection, "bank");

        connection.add(new AdvancedMessageListener() {
            @Override
            public void regularMessageReceived(SpreadMessage msg) {
                if (role == "backup"){
                    System.out.println("> New Update");/*
                    String[] split = new String(msg.getData()).split(" ");
                    bank.setBalance(Double.parseDouble(split[1]));
                    System.out.println("> State Transfer Complete");
                    System.out.println("> Balance: " + bank.balance());*/
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
            ClientHandler clientSock = new ClientHandler(s,accounts,connection);
            new Thread(clientSock).start();
        }

    }
}

class ClientHandler implements Runnable {
    private final Socket s;
    private HashMap<Integer,BankAccount> accounts;
    SpreadConnection connection;

    public boolean transfer(double amount, BankAccount origin, BankAccount destination){
        if (origin.movement(-amount)){
            destination.movement(amount);
            return true;
        } else return false;
    }

    public ClientHandler(Socket socket, HashMap map, SpreadConnection c)
    {
        this.s = socket;
        this.accounts = map;
        this.connection = c;
    }

    @Override
    public void run() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String data = br.readLine();

            PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));

            while (data != null && !data.equals("exit")) {
                //System.out.println("> Received " + data);
                String[] split = data.split(" ");

                if (split[0].equals("new")) {
                    BankAccount bank = new BankAccount();
                    accounts.put(s.getPort(), bank);
                    pw.println(s.getPort());
                    pw.flush();
                } else if (split[0].equals("balance")) {
                    BankAccount bank = accounts.get(s.getPort());
                    double r = bank.balance();
                    pw.println(r);
                    pw.flush();
                } else if (split[0].equals("movement")) {
                    BankAccount bank = accounts.get(s.getPort());
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
                } else if (split[0].equals("transfer")) {
                    BankAccount origin = accounts.get(s.getPort());
                    BankAccount destination = accounts.get(split[2]);
                    double amount = Double.parseDouble(split[1]);
                    boolean r = transfer(amount,origin,destination);
                    pw.println(r);
                }
                data = br.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}