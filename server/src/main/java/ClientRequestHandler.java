import io.atomix.utils.net.Address;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import protocol.Protocol;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class ClientRequestHandler {

    private final Bank bank;

    private final ReplicationHandler replicationHandler;

    // HashSet that contains all accounts that are currently locked
    private final Set<Integer> accountsLocked;

    // Queue that contains all the client requests that are currently on hold to be processed
    private final Queue<BiConsumer<Address, Protocol.Operation>> clientRequestsOnHold;

    private Network network = null;

    public ClientRequestHandler(Bank bank, ReplicationHandler replicationHandler) {
        this.bank = bank;
        this.replicationHandler = replicationHandler;
        accountsLocked = new HashSet<>();
        clientRequestsOnHold = new LinkedBlockingQueue<>();
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

            int accountId = m.getAccountId();
            int amount = m.getOperationInfo().getValue();
            String description = m.getOperationInfo().getDescription();

            System.out.println("> Movement Operation " + m.getOperationId() + " on the account " + accountId
                    + " with the amount: " + amount + " and description: " + description
                    + " from " + a + ".");

            if (accountsLocked.contains(accountId)){

            }

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
                        ProtocolUtil.createStateTransferFromMovementOperation(stateTransferId, accountId, movementInfo),
                        clientResponse
                );

                clientResponse.thenAccept(v -> {
                    System.out.println(Colors.ANSI_BLUE + "> The client request is complete and the state is" +
                            " updated in all backup servers, sending answer to client." + Colors.ANSI_RESET);
                    movementResponse(network, a, m, true);
                });

            }else { // Send immediately the response to the client
                movementResponse(network, a, m, false);
            }
        });
    }

    private void movementResponse(Network network, Address a, Protocol.Operation m, boolean opResult) {
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
                        ProtocolUtil.createStateTransferFromTransferOperation(
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
        });
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
                        ProtocolUtil.createStateTransferFromInterestCreditOperation(
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
        });
    }

    private void interestCreditResponse(Network network, Address a, Protocol.Operation m) {
        Protocol.OperationReply operationReply = Protocol.OperationReply.newBuilder()
                .setOperationId(m.getOperationId())
                .setType(Protocol.OperationType.INTEREST_CREDIT)
                .build();

        network.send(a, "interest-credit-response", operationReply)
                .thenRun(()->{
                    System.out.println("> Interest Credit Operation " + operationReply.getOperationId() + " response sent.");
                })
                .exceptionally(t->{
                    t.printStackTrace();
                    return null;
                });
    }
}
