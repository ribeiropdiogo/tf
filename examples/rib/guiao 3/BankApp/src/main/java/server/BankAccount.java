package server;

public class BankAccount implements Account{

    double balance;

    public BankAccount(){
        this.balance = 0;
    }

    public void setBalance(double b){
        this.balance = b;
    }

    public double balance() {
        return this.balance;
    }

    public boolean movement(double amount) {
        if (amount < 0.0){
            if (this.balance + amount < 0.0)
                return false;
            else {
                this.balance += amount;
                return true;
            }
        } else {
            this.balance += amount;
            return true;
        }
    }
}
