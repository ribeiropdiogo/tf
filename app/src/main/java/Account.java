import org.apache.commons.lang3.tuple.ImmutablePair;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Account {

    private final int id;
    private int balance;
    private final AccountStatement statement;
    private int lastMovementId;

    private final Lock accountLock;

    public Account(int id){
        this.id = id;
        balance = 0;
        this.statement = new AccountStatement();
        this.accountLock = new ReentrantLock();
        this.lastMovementId = 0;
    }

    public int balance(){
        return balance;
    }

    public ImmutablePair<Boolean, MovementInfo> deposit(int value, String description, Instant timestamp){
        balance += value;

        // Create a movement info
        MovementInfo movementInfo = new MovementInfo(value, description, timestamp, balance);

        // Add the movement info to the account statement
        this.statement.addMovement(movementInfo);

        // Store only the last 10 movements
        if (this.statement.getMovements().size() > 10){
            this.statement.getMovements().remove(0);
        }

        lastMovementId++;

        return new ImmutablePair<>(true, movementInfo);
    }

    public ImmutablePair<Boolean, MovementInfo> withdraw(int value, String description, Instant timestamp){
        if (value <= balance){
            balance -= value;

            // Create a movement info
            MovementInfo movementInfo = new MovementInfo(-value, description, timestamp, balance);

            // Add the movement info to the account statement
            this.statement.addMovement(movementInfo);

            // Store only the last 10 movements
            if (this.statement.getMovements().size() > 10){
                this.statement.getMovements().remove(0);
            }

            lastMovementId++;

            return new ImmutablePair<>(true, movementInfo);
        }else{
            return new ImmutablePair<>(false, null);
        }
    }

    public AccountStatement getAccountStatement(){
        List<MovementInfo> movementInfoList = this.statement.getMovements();
        AccountStatement newAccountStatement = new AccountStatement();

        for (MovementInfo movementInfo : movementInfoList){
            MovementInfo newMovementInfo = new MovementInfo(
                    movementInfo.getValue(), movementInfo.getDescription(), movementInfo.getTimestamp(), movementInfo.getBalanceAfter()
            );

            newAccountStatement.addMovement(newMovementInfo);
        }
        return newAccountStatement;
    }

    public void addMovementInfo(MovementInfo movementInfo){
        this.statement.addMovement(movementInfo);
    }

    public int getId(){
        return id;
    }

    public void setBalance(int balance) {
        this.balance = balance;
    }

    public void lock(){
        this.accountLock.lock();
    }

    public void unlock(){
        this.accountLock.unlock();
    }
}
