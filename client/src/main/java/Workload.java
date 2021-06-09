import java.io.IOException;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

// TODO
public class Workload {

    private final Random rand;
    private static final int NR_REQUESTS = 1000;

    // Client Stub
    private final Stub stub;

    public Workload(int port, Random rand) throws ExecutionException, InterruptedException {
        this.rand = rand;
        this.stub = new Stub(port);
    }

    public void start() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        for(int i = 0; i < NR_REQUESTS; i++){

            // Generate a int value
            int value = 1 + rand.nextInt((400 - 1) + 1);

            switch (rand.nextInt(2)){
                // Deposit operation
                case 0:
                    /*
                        boolean depositResult = stub.movement(value);
                        if (depositResult){

                        }
                     */

                    break;
                // Withdraw operation
                case 1:
                    /*
                        boolean withdrawResult = stub.movement(-value);
                        if (withdrawResult){

                        }
                     */
                    break;
            }
        }

        // Close connection
        System.out.println("> Closing connection.");
        stub.close();
    }
}
