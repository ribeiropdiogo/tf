import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Benchmarks {
    public static void main(String[] args) throws ExecutionException, InterruptedException, IOException, TimeoutException {
        Random rand = new Random();
        Workload w = new Workload(12320,rand);
        w.start();
    }
}
