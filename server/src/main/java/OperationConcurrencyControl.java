import io.atomix.utils.net.Address;
import org.apache.commons.lang3.tuple.ImmutablePair;
import protocol.Protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class OperationConcurrencyControl {

    // HashSet that contains all accounts that are currently locked
    private final Set<Integer> accountsLocked;

    // Queue that contains all the client requests that are currently on hold to be processed, the address is
    // the address of the client and the operation is the client request
    private final Queue<ImmutablePair<Address, Protocol.Operation>> clientRequestsOnHold;

    public OperationConcurrencyControl() {
        this.accountsLocked = ConcurrentHashMap.newKeySet();
        this.clientRequestsOnHold = new ConcurrentLinkedQueue<>();
    }

    public boolean acquireLock(Address address, Protocol.Operation operation){
        // Verify if there is an lock in some account where the operation requires
        if (verifyLockAndAcquire(operation)){
            return true;
        } else {
            // Add the request to be processed later if accounts locked found
            clientRequestsOnHold.add(new ImmutablePair<>(address, operation));
            return false;
        }
    }

    /**
     * Returns true if lock can be obtained or false in case lock can't be obtained
     * @param operation The client request
     * @return True if lock can be obtained, false if not
     */
    private boolean verifyLockAndAcquire(Protocol.Operation operation){
        switch (operation.getType()){
            case MOVEMENT:
                int accountId = operation.getAccountId();

                if (accountsLocked.contains(accountId)){
                    return false;
                } else {
                    accountsLocked.add(accountId);
                }
                break;
            case TRANSFER:
                Protocol.MoneyTransfer moneyTransfer = operation.getMoneyTransfer();
                int withdrawAccountId = moneyTransfer.getAccountWithdraw();
                int depositAccountId = moneyTransfer.getAccountDeposit();

                if (accountsLocked.contains(withdrawAccountId) || accountsLocked.contains(depositAccountId)){
                    return false;
                } else {
                    accountsLocked.add(withdrawAccountId);
                    accountsLocked.add(depositAccountId);
                }
                break;
            case INTEREST_CREDIT:
                // Verify if there is an lock in some account
                if (!accountsLocked.isEmpty()){
                    return false;
                } else {
                    List<Integer> existingAccounts = new ArrayList<>();
                    ConstantState.getAccounts().forEach(a -> existingAccounts.add(a.getId()));
                    accountsLocked.addAll(existingAccounts);
                }
                break;
        }
        return true;
    }

    // When unlocking an account return the next operation that acquires lock
    public ImmutablePair<Address, Protocol.Operation> unlock(int accountId){
        this.accountsLocked.remove(accountId);

        for (ImmutablePair<Address, Protocol.Operation> request : clientRequestsOnHold){
            if (verifyLockAndAcquire(request.getRight())){
                clientRequestsOnHold.remove(request);

                return request;
            }
        }
        // In case no request can he handled return null
        return null;
    }

    // When unlocking all accounts return the next operation that acquires lock
    public ImmutablePair<Address, Protocol.Operation> unlock(List<Integer> lockedAccounts){
        // Remove all locks
        this.accountsLocked.removeAll(lockedAccounts);


        for (ImmutablePair<Address, Protocol.Operation> request : clientRequestsOnHold){
            if (verifyLockAndAcquire(request.getRight())){
                clientRequestsOnHold.remove(request);

                return request;
            }
        }
        // In case no request can he handled return null
        return null;
    }
}
