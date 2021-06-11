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

    // Merge protocol section
    private final int MAJORITY;

    private int NR_APPLIED_MESSAGES = 0;

    private int NR_KNOWN_MESSAGES = 0;

    private boolean MERGE_PROTOCOL_RUNNING = false;

    private MergeProtocol mergeProtocol = null;

    // Represents if the current process has a transitional view installed
    // if true, then store all next messages to be processed later
    private boolean IS_IN_TRANSITIONAL_VIEW = false;

    // Represents if the state is poisoned (when I executed requests when I shouldn't be leader)
    private boolean IS_STATE_POISONED = false;

    private final List<Protocol.Operation> knownMessagesList;

    // Map that contains the state update request id as key and value the client request,
    // when I (leader) receive the safe message back can complete the client request and remove it
    private final Map<String, CompletableFuture<Void>> clientsRequests;

    // Handler for client's request via tcp socket
    private final ClientRequestHandler clientRequestHandler;

    public ReplicationHandler(Bank bank, String serverId, NetworkGroup network, int majority) {
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
                    processOperation(operation);
                } catch (InvalidProtocolBufferException e) {
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

    private void processOperation(Protocol.Operation operation){
        try {
            switch (operation.getType()){
                case STATE_UPDATE:
                    // If I am the leader and I received my SAFE message back then all other members
                    // of the group received the message if I am not in a transitional view
                    if (IS_LEADER){
                        // If the merge protocol is running save the received message to be handled later
                        // when a leader is determined
                        if (MERGE_PROTOCOL_RUNNING){
                            // Increment the number of known messages
                            NR_KNOWN_MESSAGES++;

                            // Add the STATE UPDATE operation to be replayed later
                            knownMessagesList.add(operation);
                        }else {
                            // If I am currently in a transitional view store all messages to be replayed
                            // later when I am the leader
                            if (IS_IN_TRANSITIONAL_VIEW){
                                System.out.println(Colors.ANSI_PURPLE + "> Received my SAFE message back, in a transitional view." +
                                        " Saving this message to be replayed later." + Colors.ANSI_RESET);

                                // Increment the number of known messages
                                NR_KNOWN_MESSAGES++;

                                // Add the STATE UPDATE operation to be replayed later
                                knownMessagesList.add(operation);
                            } else {
                                System.out.println(Colors.ANSI_BLUE + "> Received my SAFE message back, can complete the client request." + Colors.ANSI_RESET);

                                // Increment the number of applied messages
                                NR_APPLIED_MESSAGES++;

                                // Complete the client request
                                clientsRequests.get(operation.getStateUpdate().getStateTransferId()).complete(null);
                                clientsRequests.remove(operation.getStateUpdate().getStateTransferId());
                            }
                        }
                    }else {
                        switch (operation.getStateUpdate().getType()){
                            case MOVEMENT_OPERATION:
                                System.out.println("> Received a STATE UPDATE about a MOVEMENT Operation on the " +
                                        "account " + operation.getStateUpdate().getAccountId() + ".");
                                MovementInfo movementInfo = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                        operation.getStateUpdate().getStateInfo(0)
                                );
                                // Update the state of the account
                                bank.updateAccountState(operation.getStateUpdate().getAccountId(), movementInfo);
                                break;
                            case TRANSFER_OPERATION:
                                int withdrawAccId = operation.getStateUpdate().getTransfer().getAccountWithdraw();
                                int depositAccId = operation.getStateUpdate().getTransfer().getAccountDeposit();
                                System.out.println("> Received a STATE UPDATE about a TRANSFER Operation between " +
                                        "withdraw account " + withdrawAccId  + " and deposit account " +  depositAccId + ".");
                                MovementInfo withdrawAccInfo = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                        operation.getStateUpdate().getStateInfo(0)
                                );
                                MovementInfo depositAccInfo = ProtocolUtil.convertProtocolMovementInfoToMovementInfo(
                                        operation.getStateUpdate().getStateInfo(1)
                                );
                                // Update the state of the accounts
                                bank.updateAccountState(withdrawAccId, withdrawAccInfo);
                                bank.updateAccountState(depositAccId, depositAccInfo);
                                break;
                            case INTEREST_CREDIT_OPERATION:
                                Map<Integer, Protocol.MovementInfo> appliedCreditAccounts = operation.getStateUpdate().getAppliedCreditAccountsMap();
                                Set<Integer> accountIds = appliedCreditAccounts.keySet();
                                System.out.println("> Received a STATE UPDATE about a INTEREST CREDIT Operation on" +
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

                                if (knownMessagesList.size() > 0){
                                    System.out.println(Colors.ANSI_BLUE + "> I HAVE KNOWN MESSAGES TO PROCESS. "
                                            + "Initializing REPLAY!" + Colors.ANSI_RESET);
                                    replay();
                                }
                            } else {
                                System.out.println(Colors.ANSI_GREEN + ">> I AM the LEADER. Opening socket..." + Colors.ANSI_RESET);
                                IS_LEADER = true;

                                // Open the socket since I am the leader
                                clientRequestHandler.openSocket();
                            }
                        } else {
                            System.out.println(Colors.ANSI_CYAN + "> I AM NOT the LEADER. Verifying if I need to " +
                                    "request STATE from LEADER..." + Colors.ANSI_RESET);

                            // I applied message
                            if (knownMessagesList.size() > 0){
                                IS_STATE_POISONED = true;
                                System.out.println(Colors.ANSI_RED + "> I processed requests as LEADER that I " +
                                        "SHOULDN'T. MY STATE IS POISONED." + Colors.ANSI_RESET);
                                // Clear all known messages
                                knownMessagesList.clear();
                                NR_KNOWN_MESSAGES = 0;
                            }

                            // Create a state transfer request operation
                            Protocol.Operation stateTransferRequestOperation = null;

                            if(IS_STATE_POISONED) {
                                System.out.println(Colors.ANSI_RED + "> MY STATE IS POISONED. NEED TO REQUEST FOR" +
                                        " FULL STATE TRANSFER!" + Colors.ANSI_RESET);

                                // Create the state transfer request message
                                stateTransferRequestOperation = Protocol.Operation.newBuilder()
                                        .setType(Protocol.OperationType.STATE_TRANSFER_REQUEST)
                                        .setStateTransferRequest(
                                                Protocol.StateTransferRequest.newBuilder()
                                                        .setServerId(serverId)
                                                        .setType(Protocol.StateTransferRequestType.FULL_STATE)
                                                        .build()
                                        )
                                        .build();
                            }
                            else {
                                MergeProtocolProposal myProposal = mergeProtocol.getProposal(serverId);

                                // Verify if I need to request the state, if my state is not contaminated and
                                // my proposal is equal to the winner, then no need to request for state
                                if (myProposal.getNR_MESSAGES_APPLIED() == winner.getNR_MESSAGES_APPLIED() &&
                                        myProposal.getNR_MESSAGES_KNOWN() == winner.getNR_MESSAGES_KNOWN()){
                                    System.out.println(Colors.ANSI_BLUE + "> My MERGE PROTOCOL PROPOSAL is EQUAL " +
                                            " to the WINNER. NO NEED TO REQUEST STATE!" + Colors.ANSI_RESET);
                                }else {
                                    // Get my accounts last observed state
                                    Map<Integer, Integer> myObservedState = bank.getAccountsLastObservedState();
                                    System.out.println(Colors.ANSI_RED + "> I'm just MISSING SOME STATE. Doing request" +
                                            " for INCREMENTAL STATE TRANSFER!" + Colors.ANSI_RESET);

                                    // Create the state transfer request message, passing my last observed state
                                    stateTransferRequestOperation = Protocol.Operation.newBuilder()
                                            .setType(Protocol.OperationType.STATE_TRANSFER_REQUEST)
                                            .setStateTransferRequest(
                                                    Protocol.StateTransferRequest.newBuilder()
                                                            .setServerId(serverId)
                                                            .putAllLastObservedStates(myObservedState)
                                                            .setType(Protocol.StateTransferRequestType.INCREMENTAL_STATE)
                                                            .build()
                                            )
                                            .build();
                                }
                            }
                            try {
                                if (stateTransferRequestOperation != null){
                                    // Send the request to be handled by the leader
                                    networkGroup.sendSafe("server-group", stateTransferRequestOperation);
                                    System.out.println(Colors.ANSI_GREEN + "> STATE TRANSFER REQUEST sent for LEADER!" + Colors.ANSI_RESET);
                                }
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

                        Map<Integer, AccountStatement> bankState = null;

                        //Incremental State Request
                        if (stateRequest.getType() == Protocol.StateTransferRequestType.INCREMENTAL_STATE) {
                            System.out.println(Colors.ANSI_BLUE + "> Received a INCREMENTAL STATE TRANSFER REQUEST from "
                                    + stateRequest.getServerId() + "!" + Colors.ANSI_RESET);

                            bankState = bank.getBankPartialState(
                                    stateRequest.getLastObservedStatesMap());
                        } else if(stateRequest.getType() == Protocol.StateTransferRequestType.FULL_STATE) {
                            System.out.println(Colors.ANSI_BLUE + "> Received a FULL STATE TRANSFER REQUEST from "
                                    + stateRequest.getServerId() + "!" + Colors.ANSI_RESET);

                            bankState = bank.getBankState();
                        }

                        assert bankState != null;
                        stateOperationReply = Protocol.Operation.newBuilder()
                                .setType(Protocol.OperationType.STATE_TRANSFER_REPLY)
                                .setStateTransferReply(
                                        Protocol.StateTransferReply.newBuilder()
                                                .setServerId(stateRequest.getServerId())
                                                .putAllAccountsStates(
                                                        ProtocolUtil.convertBankStateToProtocol(bankState)
                                                )
                                                .build())
                                .build();

                        // Send the reply
                        try {
                            networkGroup.sendSafe("server-group", stateOperationReply);
                            System.out.println(Colors.ANSI_GREEN + "> Sent STATE TRANSFER REPLY to " + stateRequest.getServerId() + Colors.ANSI_RESET);
                        } catch (SpreadException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case STATE_TRANSFER_REPLY:
                    Protocol.StateTransferReply stateReply = operation.getStateTransferReply();

                    if(stateReply.getServerId().equals(serverId)){
                        System.out.println(Colors.ANSI_BLUE + "> Received the STATE TRANSFER REPLY from the LEADER!" + Colors.ANSI_RESET);

                        Map<Integer, Protocol.AccountStatement> mapAccountStatement = stateReply.getAccountsStatesMap();

                        for(Map.Entry<Integer, Protocol.AccountStatement> e : mapAccountStatement.entrySet()) {
                            for (Protocol.MovementInfo movement : e.getValue().getMovementsList()) {
                                bank.updateAccountState(e.getKey(), ProtocolUtil.convertProtocolMovementInfoToMovementInfo(movement));
                                NR_APPLIED_MESSAGES++;
                            }
                        }
                        System.out.println(Colors.ANSI_GREEN + "> APPLIED with SUCCESS the received STATE!" + Colors.ANSI_RESET);
                    }
                    break;
                default:
                    System.err.println("> Unknown operation!");
                    break;
            }
        }catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Replay all known messages
    private void replay(){
        for (Protocol.Operation operation : knownMessagesList){
            processOperation(operation);
            knownMessagesList.remove(operation);
        }
    }

    public void updateReplicasState(String requestUUID, Protocol.StateUpdate stateUpdateMessage, CompletableFuture<Void> clientRequest){

        // Add the client request to the clients requests maps
        this.clientsRequests.put(requestUUID, clientRequest);

        // Create the state update operation
        Protocol.Operation stateUpdateOperation = Protocol.Operation.newBuilder()
                .setType(Protocol.OperationType.STATE_UPDATE)
                .setStateUpdate(stateUpdateMessage)
                .build();

        try {
            // Send the state update message and only reply to the client when I receive my message back
            // or receive a view update before my message come back
            networkGroup.sendSafe("server-group", stateUpdateOperation);

            System.out.println(Colors.ANSI_GREEN + "> STATE UPDATE Operation message sent to all backup servers." + Colors.ANSI_RESET);

        } catch (SpreadException e) {
            e.printStackTrace();
        }
    }
}
