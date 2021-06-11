import io.atomix.utils.net.Address;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import protocol.Protocol;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class ClientRequestHandler {

    private final Bank bank;

    private final ReplicationHandler replicationHandler;

    // Concurrency control
    private final OperationConcurrencyControl concurrencyControl;

    private Network network = null;

    public ClientRequestHandler(Bank bank, ReplicationHandler replicationHandler) {
        this.bank = bank;
        this.replicationHandler = replicationHandler;
        this.concurrencyControl = new OperationConcurrencyControl();
    }


    /******************************************************************************************************
     *                              SOCKET CODE TO COMMUNICATE WITH THE CLIENT                            *
     ******************************************************************************************************/

    public void openSocket() throws ExecutionException, InterruptedException {

        // Open the network socket
        this.network = new Network(MainServerInfo.SERVER_PORT);

        // Register handlers
        registerHandlers(this.network);

        // Start listening for requests
        network.start();
    }

    public void closeSocket() throws ExecutionException, InterruptedException {
        if (network != null){
            network.close();
        }
    }

    private void registerHandlers(Network socket){
        movementHandler(socket);
        balanceHandler(socket);
        transferHandler(socket);
        accountStatementHandler(socket);
        interestCreditHandler(socket);
    }

    /******************************************************************************
     *                           BALANCE OPERATION HANDLER                        *
     ******************************************************************************/

    private void balanceHandler(Network network){
        network.registerRequestHandler("balance-request", (a, m) -> {

            int accountId = m.getAccountId();

            System.out.println("> Balance Operation " + m.getOperationId() + " in the account " + accountId
                    + " from " + a + ".");

            int balance = bank.balance(m.getAccountId());

            Protocol.OperationReply operationReply = Protocol.OperationReply.newBuilder()
                    .setOperationId(m.getOperationId())
                    .setType(Protocol.OperationType.BALANCE)
                    .setAccountId(m.getAccountId())
                    .setValue(balance)
                    .build();

            network.send(a, "balance-response", operationReply)
                    .thenRun(()->{
                        System.out.println("> Balance Operation " + m.getOperationId() + " on the account "
                                + m.getAccountId() + " response sent: " + balance + "!");
                    })
                    .exceptionally(t->{
                        t.printStackTrace();
                        return null;
                    });
        });
    }

    /******************************************************************************
     *                           MOVEMENT OPERATION HANDLER                       *
     ******************************************************************************/

    private void movementHandler(Network network){
        network.registerRequestHandler("movement-request", (a, m) -> {
            if (concurrencyControl.acquireLock(a, m)){
                System.out.println("> No concurrency, executing the Movement Operation...");
                processMovementRequest(a, m);
            } else {
                System.out.println("> Concurrency detected, saving this Movement Operation...");
            }
        });
    }

    private void processMovementRequest(Address a, Protocol.Operation m){
        int accountId = m.getAccountId();
        int amount = m.getOperationInfo().getValue();
        String description = m.getOperationInfo().getDescription();

        System.out.println("> Movement Operation " + m.getOperationId() + " on the account " + accountId
                + " with the amount: " + amount + " and description: " + description
                + " from " + a + ".");

        // Obtain the time from an external NTP server and execute the movement
        ImmutablePair<Boolean, MovementInfo> opResult = bank.movement(
                accountId, amount, description, NTPTime.getNTPTimestamp()
        );

        // If the operation had success send the state update to backup servers
        if (opResult.getLeft()){

            // Create a completable future for the client request response
            CompletableFuture<Void> clientResponse = new CompletableFuture<>();

            // Start the replication to other replicas
            String stateTransferId = UUID.randomUUID().toString();
            MovementInfo movementInfo = opResult.getRight();
            replicationHandler.updateReplicasState(
                    stateTransferId,
                    ProtocolUtil.createStateUpdateFromMovementOperation(stateTransferId, accountId, movementInfo),
                    clientResponse
            );

            clientResponse.thenAccept(v -> {
                System.out.println(Colors.ANSI_BLUE + "> The client request is complete and the state is" +
                        " updated in all backup servers, sending answer to client." + Colors.ANSI_RESET);
                movementResponse(a, m, true);
            });

        }else { // Send immediately the response to the client
            movementResponse(a, m, false);
        }
    }

    private void movementResponse(Address a, Protocol.Operation m, boolean opResult) {
        Protocol.OperationReply operationReply = Protocol.OperationReply.newBuilder()
                .setOperationId(m.getOperationId())
                .setType(Protocol.OperationType.MOVEMENT)
                .setAccountId(m.getAccountId())
                .setResult(opResult)
                .build();

        network.send(a, "movement-response", operationReply)
                .thenRun(()->{
                    System.out.println("> Movement Operation " + operationReply.getOperationId()
                            + " response sent -> " + opResult);

                    // Process other requests on hold and unlock account
                    ImmutablePair<Address, Protocol.Operation> request = concurrencyControl.unlock(m.getAccountId());
                    processRequestOnHold(request);
                })
                .exceptionally(t->{
                    t.printStackTrace();
                    return null;
                });
    }

    /******************************************************************************
     *                            TRANSFER OPERATION HANDLER                      *
     ******************************************************************************/

    private void transferHandler(Network network){
        network.registerRequestHandler("transfer-request", (a, m) -> {
            if (concurrencyControl.acquireLock(a, m)){
                System.out.println("> No concurrency, executing the Transfer Operation...");
                processTransferRequest(a, m);
            } else {
                System.out.println("> Concurrency detected, saving this Transfer Operation...");
            }
        });
    }

    private void processTransferRequest(Address a, Protocol.Operation m){
        int withdrawAccountId = m.getMoneyTransfer().getAccountWithdraw();
        int depositAccountId = m.getMoneyTransfer().getAccountDeposit();
        int amount = m.getOperationInfo().getValue();
        String description = m.getOperationInfo().getDescription();

        System.out.println("> Transfer Operation " + m.getOperationId() + " between " + withdrawAccountId + " and "
                + depositAccountId + " with the amount " + amount + " and description: " + description
                + " from " + a + ".");

        // Obtain the time from an external NTP server and make the transfer
        ImmutableTriple<Boolean, MovementInfo, MovementInfo> opResult = bank.transfer(
                withdrawAccountId, depositAccountId, amount, description, NTPTime.getNTPTimestamp()
        );

        // If the operation had success send the state update to backup servers
        if (opResult.getLeft()){

            // Create a completable future for the client request response
            CompletableFuture<Void> clientResponse = new CompletableFuture<>();

            // Start the replication to other replicas
            String stateTransferId = UUID.randomUUID().toString();
            MovementInfo withdrawMovementInfo = opResult.getMiddle();
            MovementInfo depositMovementInfo = opResult.getRight();
            replicationHandler.updateReplicasState(
                    stateTransferId,
                    ProtocolUtil.createStateUpdateFromTransferOperation(
                            stateTransferId, withdrawAccountId, withdrawMovementInfo, depositAccountId, depositMovementInfo
                    ),
                    clientResponse
            );

            clientResponse.thenAccept(v -> {
                System.out.println(Colors.ANSI_BLUE + "> The client request is complete and the state is" +
                        " updated in all backup servers, sending answer to client." + Colors.ANSI_RESET);
                transferResponse(network, a, m, true);
            });

        }else { // Send immediately the response to the client
            transferResponse(network, a, m, false);
        }
    }


    private void transferResponse(Network network, Address a, Protocol.Operation m, boolean opResult) {
        Protocol.OperationReply operationReply = Protocol.OperationReply.newBuilder()
                .setOperationId(m.getOperationId())
                .setType(Protocol.OperationType.TRANSFER)
                .setResult(opResult)
                .build();

        network.send(a, "transfer-response", operationReply)
                .thenRun(()->{
                    System.out.println("> Transfer Operation " + operationReply.getOperationId() +
                            " response sent -> " + opResult);

                    int withdrawAccId = m.getMoneyTransfer().getAccountWithdraw();
                    int depositAccId = m.getMoneyTransfer().getAccountDeposit();
                    List<Integer> accountsToUnlock = new ArrayList<>();
                    accountsToUnlock.add(withdrawAccId);
                    accountsToUnlock.add(depositAccId);

                    // Process other requests on hold and unlock account
                    ImmutablePair<Address, Protocol.Operation> request = concurrencyControl.unlock(accountsToUnlock);
                    processRequestOnHold(request);
                })
                .exceptionally(t->{
                    t.printStackTrace();
                    return null;
                });
    }

    /******************************************************************************
     *                      ACCOUNT STATEMENT OPERATION HANDLER                   *
     ******************************************************************************/

    private void accountStatementHandler(Network network){
        network.registerRequestHandler("account-statement-request", (a, m) -> {

            int accountId = m.getAccountId();

            System.out.println("> Account Statement Operation " + m.getOperationId() + " on the account "
                    + accountId + " from " + a + ".");

            // Obtain the time account statement from the bank
            AccountStatement accountStatement = bank.getAccountStatement(accountId);

            // Create a protocol movements information list
            List<Protocol.MovementInfo> protocolMovementsInfoList = new ArrayList<>();

            for (MovementInfo movementInfo : accountStatement.getMovements()){
                // Create a movement info message from the protocol buffers
                Protocol.MovementInfo protocolMovementInfo = ProtocolUtil.convertMovementInfoToProtocol(movementInfo);
                protocolMovementsInfoList.add(protocolMovementInfo);
            }

            // Create the account statement from the protocol buffers
            Protocol.AccountStatement protocolAccountStatement = Protocol.AccountStatement.newBuilder()
                    .addAllMovements(protocolMovementsInfoList)
                    .build();

            // Create the operation reply message using the account statement and the account id
            Protocol.OperationReply operationReply = Protocol.OperationReply.newBuilder()
                    .setOperationId(m.getOperationId())
                    .setType(Protocol.OperationType.ACCOUNT_STATEMENT)
                    .setAccountId(accountId)
                    .setAccountStatement(protocolAccountStatement)
                    .build();

            // Send the response
            network.send(a, "account-statement-response", operationReply)
                    .thenRun(()->{
                        System.out.println("> Account Statement Operation " + m.getOperationId() + " on the account "
                                + accountId + " response sent.");
                    })
                    .exceptionally(t->{
                        t.printStackTrace();
                        return null;
                    });
        });
    }

    /******************************************************************************
     *                       INTEREST CREDIT OPERATION HANDLER                    *
     ******************************************************************************/

    private void interestCreditHandler(Network network){
        network.registerRequestHandler("interest-credit-request", (a, m) -> {

            // If all locks acquired process request
            if (concurrencyControl.acquireLock(a, m)){
                System.out.println("> No concurrency, executing the Interest Credit Operation...");
                processInterestCreditRequest(a, m);
            } else {
                System.out.println("> Concurrency detected in some accounts for the Interest Credit operation. Saving" +
                        " the request to be replayed later.");
            }
        });
    }

    private void processInterestCreditRequest(Address a, Protocol.Operation m){
        System.out.println("> Interest Credit Operation " + m.getOperationId() + " from " + a + ".");

        // Obtain the time from an external NTP server and execute the interest credit operation
        Map<Integer, MovementInfo> appliedCreditAccounts = bank.interestCredit(
                ConstantState.INTEREST_RATE, NTPTime.getNTPTimestamp()
        );

        // If the number of applied interest credit accounts is not empty send the state update
        // to backup servers on the accounts which the balance changed
        if (!appliedCreditAccounts.isEmpty()){

            // Create a completable future for the client request response
            CompletableFuture<Void> clientResponse = new CompletableFuture<>();

            // Start the replication to other replicas
            String stateTransferId = UUID.randomUUID().toString();
            replicationHandler.updateReplicasState(
                    stateTransferId,
                    ProtocolUtil.createStateUpdateFromInterestCreditOperation(
                            stateTransferId, appliedCreditAccounts
                    ),
                    clientResponse
            );

            clientResponse.thenAccept(v -> {
                System.out.println(Colors.ANSI_BLUE + "> The client request is complete and the state is" +
                        " updated in all backup servers, sending answer to client." + Colors.ANSI_RESET);
                interestCreditResponse(network, a, m);
            });

        }else { // Send immediately the response to the client
            interestCreditResponse(network, a, m);
        }
    }

    private void interestCreditResponse(Network network, Address a, Protocol.Operation m) {
        Protocol.OperationReply operationReply = Protocol.OperationReply.newBuilder()
                .setOperationId(m.getOperationId())
                .setType(Protocol.OperationType.INTEREST_CREDIT)
                .build();

        network.send(a, "interest-credit-response", operationReply)
                .thenRun(()->{
                    System.out.println("> Interest Credit Operation " + operationReply.getOperationId() + " response sent.");

                    List<Integer> existingAccounts = new ArrayList<>();
                    ConstantState.getAccounts().forEach(acc -> existingAccounts.add(acc.getId()));

                    // Process other requests on hold and unlock account
                    ImmutablePair<Address, Protocol.Operation> request = concurrencyControl.unlock(existingAccounts);
                    processRequestOnHold(request);
                })
                .exceptionally(t->{
                    t.printStackTrace();
                    return null;
                });
    }

    /******************************************************************************
     *                        REQUEST PROCESSING AFTER UNLOCK                     *
     ******************************************************************************/

    private void processRequestOnHold(ImmutablePair<Address, Protocol.Operation> request){
        if (request != null){
            System.out.println(Colors.ANSI_BLUE + "> Processing request that was on hold to be processed." + Colors.ANSI_RESET);
            switch (request.getRight().getType()){
                case MOVEMENT:
                    processMovementRequest(request.getLeft(), request.getRight());
                    break;
                case TRANSFER:
                    processTransferRequest(request.getLeft(), request.getRight());
                    break;
                case INTEREST_CREDIT:
                    processInterestCreditRequest(request.getLeft(), request.getRight());
                    break;
            }
        } else {
            System.out.println(Colors.ANSI_BLUE + "> No requests on hold can be processed." + Colors.ANSI_RESET);
        }
    }
}
