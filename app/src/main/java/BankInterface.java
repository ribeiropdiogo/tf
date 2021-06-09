import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public interface BankInterface {

    // Consult Balance operation given a account id
    Integer balance(int accountId) throws IOException, TimeoutException;

    // Movement operation (deposit / withdraw) given a account id, a value (positive / negative), a description
    // and a timestamp, it returns a pair where the left is the result and the right is the movement info useful for
    // state transfer
    ImmutablePair<Boolean, MovementInfo> movement(int accountId, int value, String description, Instant timestamp) throws IOException, TimeoutException;

    // Transfer operation between two accounts given two accounts, a value, a description and a timestamp, it returns
    // a pair where the left is the result and the right is two movements infos (one for withdraw and another for deposit)
    // useful for state transfer
    ImmutableTriple<Boolean, MovementInfo, MovementInfo> transfer(int withdrawAccountId, int depositAccountId, int value, String description, Instant timestamp) throws TimeoutException;

    // Obtain the account statement given an account id
    AccountStatement getAccountStatement(int accountId) throws TimeoutException;

    // Increment all accounts by a given interest rate and a timestamp, returns a map containing all accounts that were
    // modified (balance > 0) and the movement info useful for the state transfer
    Map<Integer, MovementInfo> interestCredit(double interestRate, Instant timestamp) throws TimeoutException;
}
