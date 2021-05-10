package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server {

    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(12345);
        BankAccount bank = new BankAccount();

        while (true){
            Socket s = ss.accept();

            System.out.println("> Connection established with " + s.getRemoteSocketAddress());
            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String data = br.readLine();
            PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()));

            while (data != null && !data.equals("exit")) {
                //System.out.println("> Received " + data);
                String[] split = data.split(" ");

                if (split[0].equals("balance")){
                    double r = bank.balance();
                    pw.println(r);
                    pw.flush();
                } else if (split[0].equals("movement")){
                    double amount = Double.parseDouble(split[1]);
                    boolean r = bank.movement(amount);
                    pw.println(r);
                    pw.flush();
                }
                data = br.readLine();
            }


            pw.println("> Connection terminated");
            pw.flush();

            s.close();
        }
    }
}
