import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Benchmark {
    public static void main(String[] args) throws ExecutionException, InterruptedException, TimeoutException {

        System.out.println("\n\n> Usage: benchmark [port] [nr-requests]\n\n");

        if (args.length != 2){
            System.err.println("\n\n> You must provide the benchmark port and the number of requests.");
            return;
        }

        // Parse the benchmark port
        int port = Integer.parseInt(args[0]);

        // Parse the nr of requests
        int nr_requests = Integer.parseInt(args[1]);

        Random rand = new Random();
        Workload w = new Workload(port, nr_requests, rand);
        w.start();
    }
}
