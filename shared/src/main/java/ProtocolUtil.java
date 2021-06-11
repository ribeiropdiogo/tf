import com.google.protobuf.Timestamp;
import protocol.Protocol;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtocolUtil {

    public static Protocol.MovementInfo convertMovementInfoToProtocol(MovementInfo movementInfo){

        return Protocol.MovementInfo.newBuilder()
                .setMovementValue(movementInfo.getValue())
                .setBalanceAfter(movementInfo.getBalanceAfter())
                .setDescription(movementInfo.getDescription())
                .setDateHour(
                        Timestamp.newBuilder()
                                .setSeconds(movementInfo.getTimestamp().getEpochSecond())
                                .setNanos(movementInfo.getTimestamp().getNano())
                                .build()
                )
                .build();
    }

    public static MovementInfo convertProtocolMovementInfoToMovementInfo(Protocol.MovementInfo protocolMovementInfo){

        return new MovementInfo(
                protocolMovementInfo.getMovementValue(),
                protocolMovementInfo.getDescription(),
                Instant.ofEpochSecond(
                        protocolMovementInfo.getDateHour().getSeconds(),
                        protocolMovementInfo.getDateHour().getNanos()
                ),
                protocolMovementInfo.getBalanceAfter()
        );
    }

    public static Protocol.StateUpdate createStateUpdateFromMovementOperation(
            String stateTransferId, int accountId, MovementInfo movementInfo
    ){

        return Protocol.StateUpdate.newBuilder()
                .setStateTransferId(stateTransferId)
                .setType(Protocol.StateUpdateType.MOVEMENT_OPERATION)
                .setAccountId(accountId)
                .addStateInfo(convertMovementInfoToProtocol(movementInfo))
                .build();
    }

    public static Protocol.StateUpdate createStateUpdateFromTransferOperation(
            String stateTransferId, int withdrawAccId, MovementInfo withdrawAccountMovInfo,
            int depositAccId, MovementInfo depositAccountMovInfo
    ){

        return Protocol.StateUpdate.newBuilder()
                .setStateTransferId(stateTransferId)
                .setType(Protocol.StateUpdateType.TRANSFER_OPERATION)
                .setTransfer(Protocol.MoneyTransfer.newBuilder()
                        .setAccountWithdraw(withdrawAccId)
                        .setAccountDeposit(depositAccId)
                        .build()
                )
                .addStateInfo(convertMovementInfoToProtocol(withdrawAccountMovInfo))
                .addStateInfo(convertMovementInfoToProtocol(depositAccountMovInfo))
                .build();
    }

    public static Protocol.StateUpdate createStateUpdateFromInterestCreditOperation(
            String stateTransferId, Map<Integer, MovementInfo> appliedCreditAccounts){

        Map<Integer, Protocol.MovementInfo> appliedCreditAccountsProtocol = new HashMap<>();

        // Populate map
        appliedCreditAccounts.forEach((k, v) -> appliedCreditAccountsProtocol.put(k, convertMovementInfoToProtocol(v)));

        return Protocol.StateUpdate.newBuilder()
                .setStateTransferId(stateTransferId)
                .setType(Protocol.StateUpdateType.INTEREST_CREDIT_OPERATION)
                .putAllAppliedCreditAccounts(appliedCreditAccountsProtocol)
                .build();
    }

    public static Protocol.AccountStatement convertAccountStatementToProtocol(AccountStatement accountStatement){
        List<Protocol.MovementInfo> listMov = new ArrayList<>();

        for (MovementInfo m : accountStatement.getMovements()) {
            listMov.add(convertMovementInfoToProtocol(m));
        }

        return Protocol.AccountStatement.newBuilder().addAllMovements(listMov).build();
    }
}
