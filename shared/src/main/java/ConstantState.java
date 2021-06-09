import java.util.ArrayList;
import java.util.List;

public class ConstantState {

    public static final int LAST_N_MOVEMENTS = 10;

    public static final double INTEREST_RATE = 0.1;

    public static List<Account> getAccounts(){
        List<Account> accounts = new ArrayList<>();

        accounts.add(new Account(1));
        accounts.add(new Account(2));
        accounts.add(new Account(3));
        accounts.add(new Account(4));
        accounts.add(new Account(5));
        accounts.add(new Account(6));
        accounts.add(new Account(7));
        accounts.add(new Account(8));
        accounts.add(new Account(9));
        accounts.add(new Account(10));

        return accounts;
    }
}
