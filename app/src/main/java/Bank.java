import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Bank implements BankInterface{

    private final Map<Integer, Account> accounts;

    public Bank(List<Account> accountsList){
        this.accounts = new ConcurrentHashMap<>();

        // Populate hash map
        accountsList.forEach(a -> this.accounts.put(a.getId(), a));
    }

    @Override
    public Integer balance(int accountId) {
        Account account = this.accounts.get(accountId);
        account.lock();
        int balance = account.balance();
        account.unlock();
        return balance;
    }

    /**
     * Movement on a given account given a value and a description and a timestamp provided
     * by the server.
     * @param accountId The account id to execute the movement
     * @param value The movement value, positive deposit, negative withdraw
     * @param description The description of the movement
     * @param timestamp The timestamp where the operation was executed
     * @return Returns a pair with the operation result and the movement info useful for the state transfer
     */
    @Override
    public ImmutablePair<Boolean, MovementInfo> movement(int accountId, int value, String description, Instant timestamp) {
        ImmutablePair<Boolean, MovementInfo> opResult;
        Account account = this.accounts.get(accountId);

        account.lock();

        if (value > 0){
            opResult = account.deposit(value, description, timestamp);
        }else{
            opResult = account.withdraw(-value, description, timestamp);
        }

        account.unlock();

        this.accounts.replace(accountId, account);

        return opResult;
    }

    /**
     * Transfer between two accounts given a value and a description and a timestamp provided
     * by the server.
     * @param withdrawAccountId The account id to execute the withdraw
     * @param depositAccountId The account id to execute the deposit
     * @param value The transfer value
     * @param description The description of the transfer
     * @param timestamp The timestamp where the operation was executed
     * @return Returns a pair with the transfer operation result and two movements info, one for the
     *         withdraw account and another for the deposit account useful for the state transfer
     */
    @Override
    public ImmutableTriple<Boolean, MovementInfo, MovementInfo> transfer(int withdrawAccountId, int depositAccountId, int value, String description, Instant timestamp) {
        Account withdrawAccount = this.accounts.get(withdrawAccountId);

        withdrawAccount.lock();

        // Try to withdraw money
        ImmutablePair<Boolean, MovementInfo> withdrawResult = withdrawAccount.withdraw(value, description, timestamp);

        withdrawAccount.unlock();

        // If withdraw was successful (has balance)
        if (withdrawResult.getLeft()){
            Account depositAccount = this.accounts.get(depositAccountId);

            depositAccount.lock();

            // Deposit money
            ImmutablePair<Boolean, MovementInfo> depositResult = depositAccount.deposit(value, description, timestamp);

            depositAccount.unlock();

            this.accounts.replace(withdrawAccountId, withdrawAccount);
            this.accounts.replace(depositAccountId, depositAccount);
            return new ImmutableTriple<>(true, withdrawResult.getRight(), depositResult.getRight());
        } else {
            return new ImmutableTriple<>(false, null, null);
        }
    }

    @Override
    public AccountStatement getAccountStatement(int accountId) {
        Account account = this.accounts.get(accountId);
        account.lock();
        AccountStatement accountStatement = account.getAccountStatement();
        account.unlock();
        return accountStatement;
    }

    /**
     * Method that applies an interest credit in all accounts of the bank.
     * @param interestRate The interest rate to apply in the balance of the accounts
     * @param timestamp The timestamp of the operation generated by the server
     * @return Returns a Map containing the accounts which the balance was modified (balance > 0) and
     *         the movement info associated with that account
     */
    @Override
    public Map<Integer, MovementInfo> interestCredit(double interestRate, Instant timestamp) {
        Map<Integer, MovementInfo> appliedCreditAccounts = new HashMap<>();

        for (Account account : this.accounts.values()) {
            if (account.balance() > 0){
                account.lock();
                int depositAmount = (int) Math.round(account.balance() * interestRate);
                ImmutablePair<Boolean, MovementInfo> result = account.deposit(depositAmount, "interest credit", timestamp);
                appliedCreditAccounts.put(account.getId(), result.getRight());
                account.unlock();
            }
        }
        return appliedCreditAccounts;
    }

    public void updateAccountState(int accountId, MovementInfo movementInfo){
        Account account = this.accounts.get(accountId);
        account.lock();

        account.setBalance(movementInfo.getBalanceAfter());
        account.addMovementInfo(movementInfo);
        account.unlock();
        this.accounts.replace(accountId, account);
    }
}