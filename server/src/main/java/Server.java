import spread.SpreadException;

import java.net.UnknownHostException;

public class Server {

    // Create a new bank using the constant state accounts
    private static final Bank bank = new Bank(
            ConstantState.getAccounts()
    );

    public static void main(String[] args) throws SpreadException, UnknownHostException, InterruptedException {

        System.out.println("\n\n> Usage: server [daemon-port] [server-id] [majority]\n");

        if (args.length != 3){
            System.err.println("\n\n> You must provide the daemon port, the server id and the majority" +
                    " of the servers to consider!");
            return;
        }

        // Obtain the majority to consider when a new view is installed
        int majority = Integer.parseInt(args[2]);

        // Create a connection and subscribe to membership messages
        NetworkGroup network = new NetworkGroup(Integer.parseInt(args[0]), args[1], true);

        ReplicationHandler replicationHandler = new ReplicationHandler(
                bank,
                args[1],
                network,
                majority);

        // Add listener for group updates
        replicationHandler.addListeners();

        // Join the server group
        network.joinGroup("server-group");

        System.out.println("> Server is running waiting for new connections...");

        Thread.sleep(Integer.MAX_VALUE);

        // Close connection
        network.close();
        NTPTime.close();
    }
}