import java.util.Date;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Workload {

    private final int NR_REQUESTS;

    // Benchmark Stub
    private final Stub stub;
    private final Random rand;

    public Workload(int port, int nr_requests, Random rand) throws ExecutionException, InterruptedException {
        this.stub = new Stub(port, 20, true);
        this.NR_REQUESTS = nr_requests;
        this.rand = rand;
    }

    public void start() throws ExecutionException, InterruptedException, TimeoutException {

        Date d1 = new Date();

        for(int i = 0; i < NR_REQUESTS; i++){

            // Generate int values
            int value = 1 + rand.nextInt((400 - 1) + 1);
            int rand_account = 1 + rand.nextInt((5 - 1) + 1);

            switch (rand.nextInt(6)){
                // Deposit operation
                case 0:
                    stub.movement(rand_account,value,"benchmark");
                    break;
                // Withdraw operation
                case 1:
                    stub.movement(rand_account,-value,"benchmark");
                    break;
                case 2:
                    stub.balance(rand_account);
                    break;
                case 3:
                    int rand_dest = 1 + rand.nextInt((5 - 1) + 1);
                    stub.transfer(rand_account,rand_dest,value,"benchmark");
                    break;
                case 4:
                    stub.interestCredit();
                    break;
                case 5:
                    stub.getAccountStatement(rand_account);
                    break;
            }
        }

        Date d2 = new Date();
        long seconds = (d2.getTime()-d1.getTime())/1000;

        // Close connection
        stub.close();

        double avg_rt = seconds*1000/NR_REQUESTS;
        double throughput = 0;

        if (seconds > 0)
            throughput = NR_REQUESTS/seconds;

        //Print the results
        System.out.println("> Benchmark Results");
        System.out.println("> Number of operations executed: " + NR_REQUESTS);
        System.out.println("> Execution time: " + seconds + " sec");
        System.out.println("> Throughput: " + throughput + " ops/sec");
        System.out.println("> Average Response Time: " + avg_rt + " ms");
    }
}

