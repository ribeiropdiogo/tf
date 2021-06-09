import java.util.ArrayList;
import java.util.List;

public class AccountStatement {

    private final List<MovementInfo> movements;

    public AccountStatement() {
        this.movements = new ArrayList<>();
    }

    public AccountStatement(List<MovementInfo> movements) {
        this.movements = movements;
    }

    public void addMovement(MovementInfo movementInfo){
        this.movements.add(movementInfo);
    }

    public List<MovementInfo> getMovements(){
        return movements;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }
}
