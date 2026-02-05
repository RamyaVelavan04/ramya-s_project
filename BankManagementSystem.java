import java.io.*;
import java.util.*;
import java.time.LocalDateTime;

// ================= CUSTOM EXCEPTION =================
class InsufficientBalanceException extends Exception {
    InsufficientBalanceException(String msg) {
        super(msg);
    }
}

// ================= TRANSACTION MODEL =================
class Transaction implements Serializable {
    String type;
    double amount;
    LocalDateTime date;

    Transaction(String type, double amount) {
        this.type = type;
        this.amount = amount;
        this.date = LocalDateTime.now();
    }

    public String toString() {
        return type + " | Amount: " + amount + " | Date: " + date;
    }
}

// ================= ACCOUNT MODEL =================
class Account implements Serializable {
    private int accountNumber;
    private String holderName;
    private int pin;
    private double balance;
    private ArrayList<Transaction> transactions = new ArrayList<>();

    Account(int accNo, String name, int pin) {
        this.accountNumber = accNo;
        this.holderName = name;
        this.pin = pin;
        this.balance = 0;
    }

    int getAccountNumber() {
        return accountNumber;
    }

    int getPin() {
        return pin;
    }

    double getBalance() {
        return balance;
    }

    void deposit(double amount) {
        balance += amount;
        transactions.add(new Transaction("DEPOSIT", amount));
        System.out.println("Deposit successful.");
    }

    void withdraw(double amount) throws InsufficientBalanceException {
        if (amount > balance)
            throw new InsufficientBalanceException("Insufficient balance!");
        balance -= amount;
        transactions.add(new Transaction("WITHDRAW", amount));
        System.out.println("Withdrawal successful.");
    }

    void showTransactions() {
        if (transactions.isEmpty()) {
            System.out.println("No transactions found.");
            return;
        }
        for (Transaction t : transactions)
            System.out.println(t);
    }
}

// ================= FILE UTILITY =================
class FileUtil {
    static final String FILE_NAME = "bankdata.dat";

    static void save(List<Account> accounts) {
        try (ObjectOutputStream oos =
                     new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(accounts);
        } catch (Exception e) {
            System.out.println("Error saving data.");
        }
    }

    static List<Account> load() {
        try (ObjectInputStream ois =
                     new ObjectInputStream(new FileInputStream(FILE_NAME))) {
            return (List<Account>) ois.readObject();
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}

// ================= MAIN CLASS =================
public class BankManagementSystem {

    static List<Account> accounts = FileUtil.load();

    static Account login(int accNo, int pin) {
        for (Account acc : accounts)
            if (acc.getAccountNumber() == accNo && acc.getPin() == pin)
                return acc;
        return null;
    }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        int choice;

        do {
            System.out.println("\n=== BANK MANAGEMENT SYSTEM ===");
            System.out.println("1. Create Account");
            System.out.println("2. Login");
            System.out.println("3. Exit");
            System.out.print("Enter choice: ");
            choice = sc.nextInt();

            switch (choice) {

                case 1:
                    System.out.print("Account Number: ");
                    int accNo = sc.nextInt();
                    sc.nextLine();

                    System.out.print("Account Holder Name: ");
                    String name = sc.nextLine();

                    System.out.print("Set PIN: ");
                    int pin = sc.nextInt();

                    accounts.add(new Account(accNo, name, pin));
                    FileUtil.save(accounts);
                    System.out.println("Account created successfully.");
                    break;

                case 2:
                    System.out.print("Account Number: ");
                    accNo = sc.nextInt();
                    System.out.print("PIN: ");
                    pin = sc.nextInt();

                    Account acc = login(accNo, pin);
                    if (acc == null) {
                        System.out.println("Invalid credentials.");
                        break;
                    }

                    int opt;
                    do {
                        System.out.println("\n1.Deposit 2.Withdraw 3.Balance 4.History 5.Logout");
                        opt = sc.nextInt();

                        try {
                            switch (opt) {
                                case 1:
                                    System.out.print("Amount: ");
                                    acc.deposit(sc.nextDouble());
                                    break;
                                case 2:
                                    System.out.print("Amount: ");
                                    acc.withdraw(sc.nextDouble());
                                    break;
                                case 3:
                                    System.out.println("Balance: " + acc.getBalance());
                                    break;
                                case 4:
                                    acc.showTransactions();
                                    break;
                                case 5:
                                    FileUtil.save(accounts);
                                    System.out.println("Logged out.");
                                    break;
                                default:
                                    System.out.println("Invalid option.");
                            }
                        } catch (InsufficientBalanceException e) {
                            System.out.println(e.getMessage());
                        }

                    } while (opt != 5);
                    break;

                case 3:
                    FileUtil.save(accounts);
                    System.out.println("Thank you for using the bank system.");
                    break;

                default:
                    System.out.println("Invalid choice.");
            }

        } while (choice != 3);

        sc.close();
    }
}
