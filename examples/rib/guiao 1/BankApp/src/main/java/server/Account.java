package server;

public interface Account {
    double balance();
    boolean movement(double amount);
}
