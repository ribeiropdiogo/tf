import com.google.protobuf.InvalidProtocolBufferException;
import protocol.Protocol;
import spread.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class ReplicationHandler {

    private final Bank bank;
    private final NetworkGroup networkGroup;
    private final String serverId;

    private boolean IS_LEADER = false;

    // Messages waiting to be processed and to send to the client
    // private final List<Message> pendingMessages;

    private int NR_APPLIED_MESSAGES = 0;

    private int NR_KNOWN_MESSAGES = 0;

    private final int MAJORITY;

    private boolean MERGE_PROTOCOL_RUNNING = false;

    private MergeProtocol mergeProtocol = null;

    // Represents if the current process has a transitional view installed
    // if true, then store all next messages to be processed later
    private boolean IS_IN_TRANSITIONAL_VIEW = false;

    // Represents if the state is poisoned (when I executed requests when I shouldn't be leader)
    private boolean IS_STATE_POISONED = false;

    private final List<Protocol.Operation> knownMessagesList;

    private final Map<String, CompletableFuture<Void>> clientsRequests;

    private final ClientRequestHandler clientRequestHandler;

    public ReplicationHandler(Bank bank, String serverId, NetworkGroup network, int majority, List<String> configuredServers) {
        this.bank = bank;
        this.serverId = serverId;
        this.networkGroup = network;
        this.MAJORITY = majority;

        this.knownMessagesList = new ArrayList<>();

        this.clientsRequests = new ConcurrentHashMap<>();

        this.clientRequestHandler = new ClientRequestHandler(this.bank, this);
    }

    public void addListeners(){
        networkGroup.addAdvancedListener(new AdvancedMessageListener() {

            @Override
            public void regularMessageReceived(SpreadMessage spreadMessage) {
                try {
                    Protocol.Operation operation = Protocol.Operation.parseFrom(spreadMessage.getData());

                    switch (operation.getType()){
                        case STATE_TRANSFER:
                            // If I am the leader and I received my SAFE message back then all other members
                            // of the group received the message if I am not in a transitional view
                            if (IS_LEADER){
                                // If the merge protocol is running save the received message to be handled later
                                // when a leader is determined
                                if (MERGE_PROTOCOL_RUNNING){
                                    // Increment the number of known messages
                                    NR_KNOWN_MESSAGES++;

                                    // Add the STATE transfer operation to be replayed later
                                    knownMessagesList.add(operation);
                                }else {
                                    // If I am currently in a transitional view store all messages to be replayed
                                    // later when I am the leader
                                    if (IS_IN_TRANSITIONAL_VIEW){
                                        System.out.println(Colors.ANSI_PURPLE + "> Received my SAFE message back, in a transitional view." +
                                                " Saving this message to be replayed later." + Colors.ANSI_RESET);

                                        // Increment the number of known messages
                                        NR_KNOWN_MESSAGES++;

                                        // Add the STATE transfer operation to be replayed later
                                        knownMessagesList.add(operation);
                                    } else {
                                        System.out.println(Colors.ANSI_BLUE + "> Received my SAFE message back, can complete the client request." + Colors.ANSI_RESET);

                                        // Increment the number of applied messages
                                        NR_APPLIED_MESSAGES++;

                                        // Complete the client request
                                        clientsRequests.get(operation.getStateTransfer().getStateTransferId()).complete(null);
                                        clientsRequests.remove(operation.getStateTransfer().getStateTransferId());
                                    }
                                }
                            }else {
                                switch (operation.getStateTransfer().getType()){
                                    case MOVEMENT_OPERATION:
                                        System.out.println("> Received a STATE TRANSFER about a MOVEMENT Operation on the " +
                                                "account " + operation.getStateTransfer().getAccountId() + ".");
                                        MovementInfo movementInfo = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                                operation.getStateTransfer().getStateInfo(0)
                                        );
                                        // Update the state of the account
                                        bank.updateAccountState(operation.getStateTransfer().getAccountId(), movementInfo);
                                        break;
                                    case TRANSFER_OPERATION:
                                        int withdrawAccId = operation.getStateTransfer().getTransfer().getAccountWithdraw();
                                        int depositAccId = operation.getStateTransfer().getTransfer().getAccountDeposit();
                                        System.out.println("> Received a STATE TRANSFER about a TRANSFER Operation between " +
                                                "withdraw account " + withdrawAccId  + " and deposit account " +  depositAccId + ".");
                                        MovementInfo withdrawAccInfo = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                                operation.getStateTransfer().getStateInfo(0)
                                        );
                                        MovementInfo depositAccInfo = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                                operation.getStateTransfer().getStateInfo(1)
                                        );
                                        // Update the state of the accounts
                                        bank.updateAccountState(withdrawAccId, withdrawAccInfo);
                                        bank.updateAccountState(depositAccId, depositAccInfo);
                                        break;
                                    case INTEREST_CREDIT_OPERATION:
                                        Map<Integer, Protocol.MovementInfo> appliedCreditAccounts = operation.getStateTransfer().getAppliedCreditAccountsMap();
                                        Set<Integer> accountIds = appliedCreditAccounts.keySet();
                                        System.out.println("> Received a STATE TRANSFER about a INTEREST CREDIT Operation on" +
                                                " a number of " + accountIds.size() + " accounts.");

                                        // Update the state of all the accounts that were modified
                                        for(Integer accountId : accountIds){
                                            MovementInfo movementInfoAcc = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                                    appliedCreditAccounts.get(accountId)
                                            );
                                            // Update this account state
                                            bank.updateAccountState(accountId, movementInfoAcc);
                                        }
                                        break;
                                }

                                // Increment the number of applied messages
                                NR_APPLIED_MESSAGES++;
                            }
                            break;
                        case MERGE_PROTOCOL_PROPOSAL:
                            System.out.println("> Received a MERGE PROTOCOL PROPOSAL from Server: "
                                    + operation.getMergeProtocolProposal().getServerId() + " with a Nr of Applied"
                                    + " Messages: " + operation.getMergeProtocolProposal().getNAppliedMessages() + " and"
                                    + " a Nr of Known Messages: " + operation.getMergeProtocolProposal().getNKnownMessages());

                            MergeProtocolProposal mergeProtocolProposal =
                                    new MergeProtocolProposal(
                                            operation.getMergeProtocolProposal().getServerId(),
                                            operation.getMergeProtocolProposal().getNAppliedMessages(),
                                            operation.getMergeProtocolProposal().getNKnownMessages());

                            // Add the received proposal to the merge protocol
                            mergeProtocol.addProposal(mergeProtocolProposal);

                            // After adding the received proposal verify if the merge protocol is complete, if it is
                            // then we need to verify who is the winner
                            if (mergeProtocol.isComplete()){
                                MergeProtocolProposal winner = mergeProtocol.getWinner();

                                // If the winner is me, then open the socket
                                if (winner.getPROCESS_ID().equals(serverId)){

                                    MERGE_PROTOCOL_RUNNING = false;

                                    if (IS_LEADER){
                                        System.out.println(Colors.ANSI_BLUE + "> I AM already the LEADER. Not needed "
                                                + "to open the socket, since it's already open!" + Colors.ANSI_RESET);

                                        //if ()
                                           // I am going to process "
                                              //  + " the messages saved during Merge Protocol Execution..."
                                        // TODO : Replay
                                    } else {
                                        System.out.println(Colors.ANSI_GREEN + ">> I AM the LEADER. Opening socket..." + Colors.ANSI_RESET);
                                        IS_LEADER = true;

                                        // Open the socket since I am the leader
                                        clientRequestHandler.openSocket();
                                    }
                                } else {
                                    System.out.println(Colors.ANSI_CYAN + "> I AM NOT the LEADER." + Colors.ANSI_RESET);

                                    Protocol.Operation stateTransferOperation;

                                    if(IS_STATE_POISONED) {
                                        // request state to leader
                                        stateTransferOperation = Protocol.Operation.newBuilder()
                                                .setType(Protocol.OperationType.STATE_TRANSFER_REQUEST)
                                                .setStateTransferRequest(Protocol.StateTransferRequest.newBuilder()
                                                        .setServerId(serverId)
                                                        .setType(Protocol.StateTransferRequestType.FULL_STATE)
                                                        .build())
                                                .build();
                                    }
                                    else {
                                        //get All last Movements from all accounts
                                        Map<Integer, Integer> lastMovements = bank.getAllLastMovements();

                                        // request state to leader
                                        stateTransferOperation = Protocol.Operation.newBuilder()
                                                .setType(Protocol.OperationType.STATE_TRANSFER_REQUEST)
                                                .setStateTransferRequest(Protocol.StateTransferRequest.newBuilder()
                                                                .setServerId(serverId).putAllLastObservedStates(lastMovements)
                                                                .setType(Protocol.StateTransferRequestType.INCREMENTAL_STATE)
                                                        .build())
                                                .build();
                                    }
                                    //Reply
                                    try {
                                        networkGroup.sendSafe("server-group", stateTransferOperation);

                                        System.out.println(Colors.ANSI_GREEN + "> STATE TRANSFER Incremental or full State." + Colors.ANSI_RESET);

                                    } catch (SpreadException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                            break;
                        case STATE_TRANSFER_REQUEST:
                            if(IS_LEADER) {
                                Protocol.StateTransferRequest stateRequest = operation.getStateTransferRequest();
                                Protocol.Operation stateOperationReply = null;

                                //Incremental State Request
                                if (stateRequest.getType() == Protocol.StateTransferRequestType.INCREMENTAL_STATE) {

                                    Map<Integer, AccountStatement> stateTransfer = bank.getBankPartialState(
                                            stateRequest.getLastObservedStatesMap());

                                    //Convert into Protocol message
                                    Map<Integer, Protocol.AccountStatement> protocolAccount = new HashMap<>();

                                    for (Map.Entry<Integer, AccountStatement> e : stateTransfer.entrySet()) {
                                        protocolAccount.put(e.getKey(), ProtocolUtil.convertAccountStatementToProtocol(e.getValue()));
                                    }

                                    stateOperationReply = Protocol.Operation.newBuilder()
                                            .setType(Protocol.OperationType.STATE_TRANSFER_REPLY)
                                            .setStateTransferReply(
                                                    Protocol.StateTransferReply.newBuilder()
                                                            .setServerId(stateRequest.getServerId())
                                                            .putAllAccountsStates(protocolAccount).build())
                                            .build();



                                    //Full State Request
                                } else if (stateRequest.getType() == Protocol.StateTransferRequestType.FULL_STATE) {

                                    Map<Integer, AccountStatement> stateTransfer = bank.getBankState();

                                    //Convert into Protocol message
                                    Map<Integer, Protocol.AccountStatement> protocolAccount = new HashMap<>();

                                    for (Map.Entry<Integer, AccountStatement> e : stateTransfer.entrySet()) {
                                        protocolAccount.put(e.getKey(), ProtocolUtil.convertAccountStatementToProtocol(e.getValue()));
                                    }

                                    stateOperationReply = Protocol.Operation.newBuilder()
                                            .setType(Protocol.OperationType.STATE_TRANSFER_REPLY)
                                            .setStateTransferReply(
                                                    Protocol.StateTransferReply.newBuilder()
                                                            .setServerId(stateRequest.getServerId())
                                                            .putAllAccountsStates(protocolAccount).build())
                                            .build();
                                }
                                //Reply
                                try {
                                    networkGroup.sendSafe("server-group", stateOperationReply);

                                    System.out.println(Colors.ANSI_GREEN + "> STATE TRANSFER Incremental or full State." + Colors.ANSI_RESET);

                                } catch (SpreadException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case STATE_TRANSFER_REPLY:

                            Protocol.StateTransferReply stateReply = operation.getStateTransferReply();

                            if(stateReply.getServerId().equals(serverId)){
                                Map<Integer, Protocol.AccountStatement> mapAccountStatement = stateReply.getAccountsStatesMap();

                                for(Map.Entry<Integer, Protocol.AccountStatement> e : mapAccountStatement.entrySet()) {
                                    for (Protocol.MovementInfo mov : e.getValue().getMovementsList()) {
                                        bank.updateAccountState(e.getKey(), ProtocolUtil.convertProtocolMovementInfoToMovementInfo(mov));
                                    }
                                }
                            }

                          break;
                        default:
                            System.err.println("> Unknown operation!");
                            break;
                    }
                }catch (InvalidProtocolBufferException | ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void membershipMessageReceived(SpreadMessage spreadMessage) {
                MembershipInfo membershipInfo = spreadMessage.getMembershipInfo();

                // Verify if it's a transitional view, so that mean's that the next messages
                // received might be not delivered to all processes
                if (membershipInfo.isTransition()){

                    // Now I am in a transitional view, and from now store all messages
                    // received to be processed later
                    IS_IN_TRANSITIONAL_VIEW = true;

                    System.out.println(Colors.ANSI_PURPLE + "> I'm now in a TRANSITIONAL VIEW! The next received " +
                            "MESSAGES WILL BE STORED." + Colors.ANSI_RESET);

                    // If now that someone left the group size is less than the majority then I am in a
                    // minority view and If I am the leader I should close the socket, I shouldn't set
                    // now that IS_LEADER = false, because when I receive SAFE message back and if
                    // Im the leader and I am in a transitional view I should store them
                    if (membershipInfo.getMembers().length < MAJORITY && IS_LEADER){
                        System.out.println(Colors.ANSI_PURPLE + "> I'm now in a MINORITY VIEW and I AM the LEADER! " +
                                "The SOCKET WILL BE CLOSED..." + Colors.ANSI_RESET);
                        try {
                            clientRequestHandler.closeSocket();
                        } catch (ExecutionException | InterruptedException e) {
                            System.err.println("> Error closing socket!");
                            e.printStackTrace();
                        }
                    }
                }

                // Regular membership
                if (membershipInfo.isRegularMembership()){

                    System.out.println(Colors.ANSI_BLUE + "> A REGULAR VIEW was INSTALLED." + Colors.ANSI_RESET);

                    // If it's in transitional view, then since a regular view was installed put the variable
                    // false
                    if (IS_IN_TRANSITIONAL_VIEW){
                        IS_IN_TRANSITIONAL_VIEW = false;
                    }

                    boolean IS_MAJORITY_VIEW_INSTALLED = false;

                    // When entering the group obtain all processes that are currently in the group
                    SpreadGroup[] groupMembers = membershipInfo.getMembers();

                    // Verify if it's a majority view
                    if (groupMembers.length >= MAJORITY){

                        // We are in a majority view
                        IS_MAJORITY_VIEW_INSTALLED = true;
                    }
                    // If the currently number of members of the group is less than the majority and
                    // if I am the leader I should close the socket
                    else if (membershipInfo.getMembers().length < MAJORITY && IS_LEADER){

                        System.out.println(Colors.ANSI_PURPLE + "> The group member " + membershipInfo.getLeft() +
                                " LEFT THE GROUP and I am in a MINORITY VIEW and I AM THE LEADER!" +
                                " The SOCKET WILL BE CLOSED..." + Colors.ANSI_RESET);
                        IS_LEADER = false;
                        try {
                            // Try to close the socket
                            clientRequestHandler.closeSocket();
                        } catch (ExecutionException | InterruptedException e) {
                            System.err.println("> Error closing socket!");
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println(Colors.ANSI_PURPLE + "> I AM IN a MINORITY VIEW." + Colors.ANSI_RESET);
                    }

                    // If a majority view was installed start the merge protocol and send to all
                    // processes my merge protocol proposal
                    if (IS_MAJORITY_VIEW_INSTALLED){

                        System.out.println(Colors.ANSI_BLUE + "> I am in a MAJORITY VIEW. I will EXECUTE the " +
                                "MERGE PROTOCOL to DETERMINE who should be THE LEADER!" + Colors.ANSI_RESET);

                        // Start the merge protocol and the next messages received will be stored
                        MERGE_PROTOCOL_RUNNING = true;

                        // Start the merge protocol with information about the number of processes
                        // to wait for proposals, this part of code even if a new view is installed
                        // a new merge protocol is created, discarding the running one
                        mergeProtocol = new MergeProtocol(groupMembers.length);

                        Protocol.MergeProtocolProposal myMergeProtocolProposal = Protocol.MergeProtocolProposal.newBuilder()
                                .setServerId(serverId)
                                .setNAppliedMessages(NR_APPLIED_MESSAGES)
                                .setNKnownMessages(NR_KNOWN_MESSAGES)
                                .build();

                        Protocol.Operation mergeOperationProposal = Protocol.Operation.newBuilder()
                                .setType(Protocol.OperationType.MERGE_PROTOCOL_PROPOSAL)
                                .setMergeProtocolProposal(myMergeProtocolProposal)
                                .build();

                        try {
                            // Send my proposal to all processes, and since I am using SAFE I will only
                            // save my proposal when I receive my message back, the others proposals I save
                            // when I receive a message with type MERGE_PROTOCOL_PROPOSAL
                            networkGroup.sendSafe("server-group", mergeOperationProposal);
                        } catch (SpreadException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
    }

    public void updateReplicasState(String requestUUID, Protocol.StateTransfer stateTransferMessage, CompletableFuture<Void> clientRequest){

        // Add the client request to the clients requests maps
        this.clientsRequests.put(requestUUID, clientRequest);

        // Create the state transfer operation
        Protocol.Operation stateTransferOperation = Protocol.Operation.newBuilder()
                .setType(Protocol.OperationType.STATE_TRANSFER)
                .setStateTransfer(stateTransferMessage)
                .build();

        try {
            // Send the state transfer message and only reply to the client when I receive my message back
            // or receive a view update before my message come back
            networkGroup.sendSafe("server-group", stateTransferOperation);

            System.out.println(Colors.ANSI_GREEN + "> STATE TRANSFER Operation message sent to all backup servers." + Colors.ANSI_RESET);

        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }
}
