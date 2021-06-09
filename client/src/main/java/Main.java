import java.util.concurrent.ExecutionException;

public class Main {

    public static void main(String[] args) throws ExecutionException, InterruptedException {

        System.out.println("\n\n> Usage: client [port]\n\n");

        if (args.length != 1){
            System.err.println("\n\n> You must provide the client port.");
            return;
        }

        // Parse the client port
        int port = Integer.parseInt(args[0]);

        // Create the menu
        Menu menu = new Menu(port);

        // Start the menu
        menu.menu();
    }
}
