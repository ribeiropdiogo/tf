import com.google.protobuf.Timestamp;
import protocol.Protocol;

import java.time.Instant;
import java.util.HashMap;
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

    public static Protocol.StateTransfer createStateTransferFromMovementOperation(
            String stateTransferId, int accountId, MovementInfo movementInfo
    ){

        return Protocol.StateTransfer.newBuilder()
                .setStateTransferId(stateTransferId)
                .setType(Protocol.StateTransferType.MOVEMENT_OPERATION)
                .setAccountId(accountId)
                .addStateInfo(convertMovementInfoToProtocol(movementInfo))
                .build();
    }

    public static Protocol.StateTransfer createStateTransferFromTransferOperation(
            String stateTransferId, int withdrawAccId, MovementInfo withdrawAccountMovInfo,
            int depositAccId, MovementInfo depositAccountMovInfo
    ){

        return Protocol.StateTransfer.newBuilder()
                .setStateTransferId(stateTransferId)
                .setType(Protocol.StateTransferType.TRANSFER_OPERATION)
                .setTransfer(Protocol.MoneyTransfer.newBuilder()
                        .setAccountWithdraw(withdrawAccId)
                        .setAccountDeposit(depositAccId)
                        .build()
                )
                .addStateInfo(convertMovementInfoToProtocol(withdrawAccountMovInfo))
                .addStateInfo(convertMovementInfoToProtocol(depositAccountMovInfo))
                .build();
    }

    public static Protocol.StateTransfer createStateTransferFromInterestCreditOperation(
            String stateTransferId, Map<Integer, MovementInfo> appliedCreditAccounts){

        Map<Integer, Protocol.MovementInfo> appliedCreditAccountsProtocol = new HashMap<>();

        // Populate map
        appliedCreditAccounts.forEach((k, v) -> appliedCreditAccountsProtocol.put(k, convertMovementInfoToProtocol(v)));

        return Protocol.StateTransfer.newBuilder()
                .setStateTransferId(stateTransferId)
                .setType(Protocol.StateTransferType.INTEREST_CREDIT_OPERATION)
                .putAllAppliedCreditAccounts(appliedCreditAccountsProtocol)
                .build();
    }
}
