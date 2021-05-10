package client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class Bot {
    public static void main(String[] args) {
        try{
            Socket s = new Socket("localhost",12345);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintWriter pw = new PrintWriter(s.getOutputStream());
            String response, command;
            Random rand = new Random();
            double local_amount = 0.0;

            for (int i = 0; i < 1000000; i++){
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

                //System.out.println(signal + " - " + amount + " _ " +local_amount);
                pw.println(command);
                pw.flush();
                response = in.readLine();
            }

            pw.println("balance");
            pw.flush();
            response = in.readLine();
            System.out.println("Bank says: " + response);
            System.out.println("Bot says: " + local_amount);

            s.close();

        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
