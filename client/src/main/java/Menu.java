import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public class Menu {

    private static final Scanner scan = new Scanner(System.in);;
    private final Stub stub;

    public Menu(int clientPort) throws ExecutionException, InterruptedException {
        this.stub = new Stub(clientPort, 10, false);
    }

    public void menu() {
        while (true){

            printMainMenu();

            int option = scan.nextInt();

            switch (option){
                case 1: // Movement
                    MovementOperation movementOperation = new MovementOperation();
                    // Print menu and get values
                    movementOperation.printMenu();

                    boolean opResult;
                    try {
                        opResult = this.stub.movement(
                                movementOperation.accountId, movementOperation.value, movementOperation.description
                        );
                        if (opResult){
                            System.out.println(Colors.ANSI_GREEN + "> The movement operation was successful." + Colors.ANSI_RESET);
                        }else {
                            System.out.println(Colors.ANSI_RED + "> The movement operation was not successful. " +
                                    "You don't have enough money!" + Colors.ANSI_RESET);
                        }
                    } catch (TimeoutException e) {
                        System.err.println("\n\n>> There was an error with your movement request. Please try again.\n");
                    }
                    break;

                case 2: // Transfer
                    TransferOperation transferOperation = new TransferOperation();
                    // Print menu and get values
                    transferOperation.printMenu();
                    try {
                        opResult = this.stub.transfer(
                                transferOperation.withdrawAccountId, transferOperation.depositAccountId,
                                transferOperation.amount, transferOperation.description
                        );
                        if (opResult){
                            System.out.println(Colors.ANSI_GREEN + "> The transfer operation was successful." + Colors.ANSI_RESET);
                        }else {
                            System.out.println(Colors.ANSI_RED + "> The transfer operation was not successful. " +
                                    "You don't have enough money!" + Colors.ANSI_RESET);
                        }
                    } catch (TimeoutException e) {
                        System.err.println("\n\n>> There was an error with your movement request. Please try again.\n");
                    }
                    break;

                case 3: // Account Statement
                    AccountStatementOperation accountStatementOperation = new AccountStatementOperation();
                    // Print menu and get values
                    accountStatementOperation.printMenu();
                    try {
                        AccountStatement accountStatement = this.stub.getAccountStatement(accountStatementOperation.accountId);
                        if (accountStatement != null){
                            System.out.println(Colors.ANSI_GREEN + "> The account statement operation was successful." + Colors.ANSI_RESET);

                            // Print account statement
                            printAccountStatement(accountStatementOperation.accountId, accountStatement.getMovements());
                        }else {
                            System.out.println(Colors.ANSI_RED + "> The account statement operation was not successful." + Colors.ANSI_RESET);
                        }
                    } catch (TimeoutException e) {
                        System.err.println("\n\n>> There was an error with your movement request. Please try again.\n");
                    }
                    break;

                case 4: // Interest Rate
                    InterestCreditOperation interestCreditOperation = new InterestCreditOperation();
                    // Print menu
                    interestCreditOperation.printMenu();

                    try {
                        this.stub.interestCredit();
                        System.out.println(Colors.ANSI_GREEN + "> The interest credit operation was successful." + Colors.ANSI_RESET);
                    } catch (TimeoutException e) {
                        System.err.println("\n\n>> There was an error with your movement request. Please try again.\n");
                    }
                    break;

                case 5: // Balance
                    BalanceOperation balanceOperation = new BalanceOperation();
                    // Print menu and get values
                    balanceOperation.printMenu();
                    try {
                        Integer balance = this.stub.balance(balanceOperation.accountId);
                        System.out.println(">> The account balance is: " + balance);
                    } catch (TimeoutException e) {
                        System.err.println("\n\n>> There was an error with your balance request. Please try again.\n");
                    }
                    break;

                default:
                    System.err.println("> Invalid option!\n\n");
                    break;
            }
            System.out.println("----------------------------------------------------------------------");
        }
    }

    private void printMainMenu(){
        System.out.println("-------------------------------- BANK --------------------------------");
        System.out.println("\t\t1 - Movement");
        System.out.println("\t\t2 - Transfer");
        System.out.println("\t\t3 - Account Statement");
        System.out.println("\t\t4 - Interest Rate");
        System.out.println("\t\t5 - Balance");
        System.out.println("----------------------------------------------------------------------");
    }

    private void printAccountStatement(int accountId, List<MovementInfo> movementInfoList){

        System.out.println(Colors.ANSI_BLUE + "\n> LAST " + ConstantState.LAST_N_MOVEMENTS + " MOVEMENTS..." + Colors.ANSI_RESET);
        for (MovementInfo movementInfo : movementInfoList){
            System.out.println("******************* MOVEMENT *******************");
            System.out.println("\t-> Description: " + movementInfo.getDescription());
            System.out.println("\t-> Value: " + movementInfo.getValue());
            System.out.println("\t-> Timestamp: " + movementInfo.getTimestamp());
            System.out.println("\t-> Balance After: " + movementInfo.getBalanceAfter());
            System.out.println("************************************************");
        }
    }

    /*************************************************************************************
     *                                   MOVEMENT OPERATION                              *
     *************************************************************************************/
    private static class MovementOperation{
        public int accountId;
        public int value;
        public String description;

        void printMenu(){
            System.out.println("------------------------- MOVEMENT OPERATION -------------------------");
            System.out.println("\n> Insert the Account Id (Between 1 and 10): ");
            accountId = scan.nextInt();
            System.out.println("\n> Insert the amount (positive - deposit, negative - withdraw): ");
            value = scan.nextInt();
            System.out.println("\n> Insert the operation description: ");
            description = scan.next();
        }
    }

    /*************************************************************************************
     *                                   TRANSFER OPERATION                              *
     *************************************************************************************/
    private static class TransferOperation{
        public int withdrawAccountId;
        public int depositAccountId;
        public int amount = 0;
        public String description;

        void printMenu(){
            System.out.println("------------------------- TRANSFER OPERATION -------------------------");
            System.out.println("\n> Insert the Account Id to withdraw (Between 1 and 10): ");
            withdrawAccountId = scan.nextInt();
            System.out.println("\n> Insert the Account Id to deposit (Between 1 and 10): ");
            depositAccountId = scan.nextInt();
            while (amount == 0){
                System.out.println("\n> Insert the amount to transfer: ");
                amount = scan.nextInt();
                if (amount <= 0){
                    amount = 0;
                    System.err.println("\n> Invalid amount, you must provide a positive amount to transfer.");
                }
            }
            System.out.println("\n> Insert the operation description: ");
            description = scan.next();
        }
    }

    /*************************************************************************************
     *                               ACCOUNT STATEMENT OPERATION                         *
     *************************************************************************************/
    private static class AccountStatementOperation{
        public int accountId;

        void printMenu(){
            System.out.println("--------------------- ACCOUNT STATEMENT OPERATION --------------------");
            System.out.println("\n> Insert the Account Id (Between 1 and 10): ");
            accountId = scan.nextInt();
        }
    }

    /*************************************************************************************
     *                                INTEREST CREDIT OPERATION                          *
     *************************************************************************************/
    private static class InterestCreditOperation{
        void printMenu(){
            System.out.println("---------------------- INTEREST CREDIT OPERATION ---------------------");
        }
    }

    /*************************************************************************************
     *                                    BALANCE OPERATION                              *
     *************************************************************************************/
    private static class BalanceOperation{
        public int accountId;

        void printMenu(){
            System.out.println("------------------------- BALANCE OPERATION --------------------------");
            System.out.println("\n> Insert the Account Id: ");
            accountId = scan.nextInt();
        }
    }
}
