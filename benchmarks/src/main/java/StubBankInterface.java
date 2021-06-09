import java.util.concurrent.TimeoutException;

public interface StubBankInterface {
    // Consult Balance operation given a account id
    Integer balance(int accountId) throws TimeoutException;

    // Movement operation (deposit / withdraw) given a account id, a value (positive / negative) and a description
    Boolean movement(int accountId, int value, String description) throws TimeoutException;

    // Transfer operation between two accounts given two accounts, a value and a description
    Boolean transfer(int withdrawAccountId, int depositAccountId, int value,  String description) throws TimeoutException;

    // Obtain the account statement given an account id
    AccountStatement getAccountStatement(int accountId) throws TimeoutException;

    // Increment all accounts by the interest rate defined in the server
    void interestCredit() throws TimeoutException;
}
