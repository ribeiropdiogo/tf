import io.atomix.utils.net.Address;
import protocol.Protocol;
import protocol.Protocol.*;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Stub implements StubBankInterface{

    private final Network network;

    private CompletableFuture<Integer> balanceRequest;
    private CompletableFuture<Boolean> movementRequest;
    private CompletableFuture<Boolean> transferRequest;
    private CompletableFuture<AccountStatement> accountStatementRequest;
    private CompletableFuture<Void> interestCreditRequest;


    private static final int TIMEOUT = 5;

    private Integer lastRequestId;

    public Stub(int clientPort) throws ExecutionException, InterruptedException {

        network = new Network(clientPort);

        this.lastRequestId = 0;

        start();
    }

    private void start() throws ExecutionException, InterruptedException {

        // Register all handlers for incoming messages
        registerHandlers();

        network.start();

        System.out.println("> Client started!");
    }

    private void registerHandlers(){
        // Balance Response Handler
        network.registerReplyHandler("balance-response", (address, response) -> {
            System.out.println("> Received Reply from BALANCE Operation on the Account: " + response.getAccountId() +
                    " with Operation Id: "+ response.getOperationId() + ".\n\t- Value: -> " + response.getValue());
            this.balanceRequest.complete(response.getValue());
        });

        // Movement Response Handler
        network.registerReplyHandler("movement-response", ((address, response) -> {
            System.out.println("> Received Reply from MOVEMENT Operation on the Account: " + response.getAccountId() +
                    " with Operation Id: "+ response.getOperationId() + ".\n\t- Result: -> " + response.getResult());
            this.movementRequest.complete(response.getResult());
        }));

        // Transfer Response Handler
        network.registerReplyHandler("transfer-response", ((address, response) -> {
            System.out.println("> Received Reply from TRANSFER Operation with Operation Id: "
                    + response.getOperationId() + ".\n\t- Result: -> " + response.getResult());
            this.transferRequest.complete(response.getResult());
        }));

        // Account Statement Response Handler
        network.registerReplyHandler("account-statement-response", ((address, response) -> {
            System.out.println("> Received Reply from ACCOUNT STATEMENT Operation on the Account: " + response.getAccountId()
                    + " with Operation Id: "+ response.getOperationId());

            Protocol.AccountStatement accountStatementProtocolMessage = response.getAccountStatement();
            List<Protocol.MovementInfo> movementInfoListProtocolMessage = accountStatementProtocolMessage.getMovementsList();

            AccountStatement accountStatement = new AccountStatement();

            for (Protocol.MovementInfo movementInfoProtocol : movementInfoListProtocolMessage){
                accountStatement.addMovement(
                        ProtocolUtil.convertProtocolMovementInfoToMovementInfo(movementInfoProtocol)
                );
            }

            this.accountStatementRequest.complete(accountStatement);
        }));

        // Interest Credit Response Handler
        network.registerReplyHandler("interest-credit-response", ((address, response) -> {
            System.out.println("> Received Reply from INTEREST CREDIT Operation with Operation Id: "
                    + response.getOperationId());
            this.interestCreditRequest.complete(null);
        }));
    }

    @Override
    public Integer balance(int accountId) throws TimeoutException {
        this.balanceRequest = new CompletableFuture<>();

        Operation operation = Operation.newBuilder()
                .setOperationId(lastRequestId)
                .setType(OperationType.BALANCE)
                .setAccountId(accountId)
                .build();

        // Send balance operation for the server
        network.send(Address.from(MainServerInfo.SERVER_PORT), "balance-request", operation)
                .thenRun(() -> {
                    System.out.println("> Balance operation on the account " + accountId + " with id "
                            + lastRequestId + " sent to the server.");
                    lastRequestId++;
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });

        try {
            // Try to obtain the result from the balance operation
            return this.balanceRequest.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Boolean movement(int accountId, int value, String description) throws TimeoutException {
        this.movementRequest = new CompletableFuture<>();

        BankOperation bankOperation = BankOperation.newBuilder()
                .setValue(value)
                .setDescription(description)
                .build();

        Operation operation = Operation.newBuilder()
                .setOperationId(lastRequestId)
                .setType(OperationType.MOVEMENT)
                .setAccountId(accountId)
                .setOperationInfo(bankOperation)
                .build();

        // Send the movement operation to the server
        network.send(Address.from(MainServerInfo.SERVER_PORT), "movement-request", operation)
                .thenRun(() -> {
                    System.out.println("> Movement operation on the account " + accountId + " with id "
                            + lastRequestId + " sent to server."
                            + "\n\t- Movement Value: " + value
                            + "\n\t- Movement Description: " + description);
                    lastRequestId++;
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });

        try {
            // Try to obtain the result from the movement operation
            return this.movementRequest.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public Boolean transfer(int withdrawAccountId, int depositAccountId, int value, String description) throws TimeoutException{
        this.transferRequest = new CompletableFuture<>();

        // Create a money transfer message
        MoneyTransfer moneyTransfer = MoneyTransfer.newBuilder()
                .setAccountWithdraw(withdrawAccountId)
                .setAccountDeposit(depositAccountId)
                .build();

        // Create a basic bank operation message
        BankOperation bankOperation = BankOperation.newBuilder()
                .setValue(value)
                .setDescription(description)
                .build();

        // Create the request message
        Operation operation = Operation.newBuilder()
                .setOperationId(lastRequestId)
                .setType(OperationType.TRANSFER)
                .setOperationInfo(bankOperation)
                .setMoneyTransfer(moneyTransfer)
                .build();

        // Send the transfer operation to the server
        network.send(Address.from(MainServerInfo.SERVER_PORT), "transfer-request", operation)
                .thenRun(() -> {
                    System.out.println("> Transfer operation between withdraw account " + withdrawAccountId
                            + " and deposit account " + depositAccountId + " with id "
                            + lastRequestId + " sent to server. "
                            + "\n\t- Movement Value: " + value
                            + "\n\t- Movement Description: " + description);
                    lastRequestId++;
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });

        try {
            // Try to obtain the result from the transfer operation
            return this.transferRequest.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public AccountStatement getAccountStatement(int accountId) throws TimeoutException {
        this.accountStatementRequest = new CompletableFuture<>();

        Operation operation = Operation.newBuilder()
                .setOperationId(lastRequestId)
                .setType(OperationType.ACCOUNT_STATEMENT)
                .setAccountId(accountId)
                .build();

        // Send account statement operation for the server
        network.send(Address.from(MainServerInfo.SERVER_PORT), "account-statement-request", operation)
                .thenRun(() -> {
                    System.out.println("> Account Statement operation on the account " + accountId + " with id "
                            + lastRequestId + " sent to the server.");
                    lastRequestId++;
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });

        try {
            // Try to obtain the result from the account statement operation
            return this.accountStatementRequest.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void interestCredit() throws TimeoutException {
        this.interestCreditRequest = new CompletableFuture<>();

        Operation operation = Operation.newBuilder()
                .setOperationId(lastRequestId)
                .setType(OperationType.INTEREST_CREDIT)
                .build();

        // Send interest credit operation for the server
        network.send(Address.from(MainServerInfo.SERVER_PORT), "interest-credit-request", operation)
                .thenRun(() -> {
                    System.out.println("> Interest Credit operation with id " + lastRequestId + " sent to the server.");
                    lastRequestId++;
                })
                .exceptionally(e -> {
                    e.printStackTrace();
                    return null;
                });

        try {
            // Try to obtain the result from the interest credit operation
            this.interestCreditRequest.get(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    public void close() throws ExecutionException, InterruptedException {
        network.close();
    }
}
