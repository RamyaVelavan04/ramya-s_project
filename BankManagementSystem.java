import java.io.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Bank Management System — single-file Java program
 *
 * Demonstrates OOP pillars + extras:
 * - Encapsulation: private fields + getters/setters
 * - Inheritance: Account -> SavingsAccount, CurrentAccount
 * - Abstraction: abstract Account, interfaces (InterestBearing, Identifiable)
 * - Polymorphism: operations via Account references; overridden methods
 * - Composition: Bank has Accounts & Customers; Account has Transactions
 * - Static members/inner classes: IdGenerator, utilities
 * - Generics: Repository<T>, Result<T>
 * - Enums: AccountType, TxnType
 * - Exceptions: InsufficientFundsException, DomainException
 * - Design: Simple Factory (AccountFactory), Strategy (interest calc via interface)
 * - Immutability: Transaction objects are immutable
 */
public class BankManagementSystem {
    // ----- ENTRY POINT -----
    public static void main(String[] args) {
        Bank bank = new Bank("OOP National Bank");
        SeedData.seed(bank);
        new ConsoleApp(bank).run();
    }
}

// ===== Core Domain =====
interface Identifiable {
    String getId();
}

/** Generic Result wrapper to avoid exceptions for flow control */
final class Result<T> {
    public final boolean ok;
    public final T value;
    public final String error;
    private Result(boolean ok, T value, String error) { this.ok = ok; this.value = value; this.error = error; }
    public static <T> Result<T> ok(T v) { return new Result<>(true, v, null); }
    public static <T> Result<T> err(String e) { return new Result<>(false, null, e); }
}

/**
 * Generic In-Memory Repository demonstrating Java Generics.
 */
class Repository<T extends Identifiable> implements Serializable {
    private final Map<String, T> data = new LinkedHashMap<>();
    public void save(T entity) { data.put(entity.getId(), entity); }
    public Optional<T> find(String id) { return Optional.ofNullable(data.get(id)); }
    public Collection<T> all() { return Collections.unmodifiableCollection(data.values()); }
    public boolean exists(String id) { return data.containsKey(id); }
}

/** Domain Exception hierarchy */
class DomainException extends Exception { public DomainException(String m){ super(m);} }
class InsufficientFundsException extends DomainException { public InsufficientFundsException(String m){ super(m);} }

/** Customer entity */
class Customer implements Identifiable, Serializable {
    private final String id; // Encapsulation
    private String name;
    private String email;

    public Customer(String name, String email) {
        this.id = IdGenerator.next("CUST");
        this.name = Objects.requireNonNull(name);
        this.email = Objects.requireNonNull(email);
    }
    @Override public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = Objects.requireNonNull(name); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = Objects.requireNonNull(email); }
    @Override public String toString(){ return id+" — "+name+" ("+email+")"; }
}

/** Different account kinds */
enum AccountType { SAVINGS, CURRENT }

/** Interest strategy */
interface InterestBearing { double calculateMonthlyInterest(); void applyMonthlyInterest(); }

/** Immutable Transaction */
final class Transaction implements Serializable {
    enum TxnType { DEPOSIT, WITHDRAW, TRANSFER_OUT, TRANSFER_IN, INTEREST }
    private final String id;
    private final LocalDateTime time;
    private final TxnType type;
    private final double amount;
    private final String note;

    public Transaction(TxnType type, double amount, String note) {
        this.id = IdGenerator.next("TXN");
        this.time = LocalDateTime.now();
        this.type = type;
        this.amount = amount;
        this.note = note;
    }
    public String getId(){ return id; }
    public LocalDateTime getTime(){ return time; }
    public TxnType getType(){ return type; }
    public double getAmount(){ return amount; }
    public String getNote(){ return note; }
    @Override public String toString(){
        return String.format("%s | %s | %s | %.2f | %s", id, time, type, amount, note);
    }
}

/** Abstract Account demonstrating Abstraction & Encapsulation */
abstract class Account implements Identifiable, Serializable {
    private final String id;
    private final AccountType type;
    private final Customer owner;
    protected double balance; // protected for subclasses
    private final List<Transaction> history = new ArrayList<>();

    protected Account(AccountType type, Customer owner, double openingBalance) {
        this.id = IdGenerator.next("ACC");
        this.type = type;
        this.owner = Objects.requireNonNull(owner);
        if (openingBalance < 0) throw new IllegalArgumentException("Opening balance must be >= 0");
        this.balance = openingBalance;
        history.add(new Transaction(Transaction.TxnType.DEPOSIT, openingBalance, "Opening balance"));
    }

    @Override public String getId() { return id; }
    public AccountType getType() { return type; }
    public Customer getOwner() { return owner; }
    public double getBalance() { return balance; }
    public List<Transaction> getHistory() { return Collections.unmodifiableList(history); }

    public void deposit(double amount) throws DomainException {
        validateAmount(amount);
        balance += amount;
        history.add(new Transaction(Transaction.TxnType.DEPOSIT, amount, "Cash deposit"));
    }

    public void withdraw(double amount) throws DomainException {
        validateAmount(amount);
        if (!canWithdraw(amount)) throw new InsufficientFundsException("Withdrawal would exceed limit");
        balance -= amount;
        history.add(new Transaction(Transaction.TxnType.WITHDRAW, amount, "Cash withdrawal"));
    }

    public final void transferTo(Account target, double amount) throws DomainException {
        Objects.requireNonNull(target);
        if (this == target) throw new DomainException("Cannot transfer to same account");
        validateAmount(amount);
        if (!canWithdraw(amount)) throw new InsufficientFundsException("Transfer would exceed limit");
        this.balance -= amount;
        this.history.add(new Transaction(Transaction.TxnType.TRANSFER_OUT, amount, "To " + target.getId()));
        target.balance += amount;
        target.history.add(new Transaction(Transaction.TxnType.TRANSFER_IN, amount, "From " + this.getId()));
    }

    protected void addInterestTxn(double amount) {
        if (amount != 0) history.add(new Transaction(Transaction.TxnType.INTEREST, amount, "Monthly interest"));
    }

    protected abstract boolean canWithdraw(double amount);

    private void validateAmount(double amount) throws DomainException {
        if (amount <= 0) throw new DomainException("Amount must be > 0");
    }

    @Override public String toString() {
        return String.format("%s | %-7s | %-20s | Bal: %.2f", id, type, owner.getName(), balance);
    }
}

/** SavingsAccount: has interest; no overdraft */
class SavingsAccount extends Account implements InterestBearing {
    private double annualRate; // e.g., 6% as 0.06
    public SavingsAccount(Customer owner, double openingBalance, double annualRate) {
        super(AccountType.SAVINGS, owner, openingBalance);
        this.annualRate = annualRate;
    }
    public double getAnnualRate(){ return annualRate; }
    public void setAnnualRate(double r){ if (r < 0) throw new IllegalArgumentException("rate"); this.annualRate = r; }
    @Override protected boolean canWithdraw(double amount) { return getBalance() - amount >= 0; }
    @Override public double calculateMonthlyInterest() { return getBalance() * (annualRate/12.0); }
    @Override public void applyMonthlyInterest() {
        double i = calculateMonthlyInterest();
        this.balance += i;
        addInterestTxn(i);
    }
}

/** CurrentAccount: supports overdraft */
class CurrentAccount extends Account {
    private double overdraftLimit; // positive number, e.g., 10000
    public CurrentAccount(Customer owner, double openingBalance, double overdraftLimit) {
        super(AccountType.CURRENT, owner, openingBalance);
        if (overdraftLimit < 0) throw new IllegalArgumentException("overdraftLimit");
        this.overdraftLimit = overdraftLimit;
    }
    public double getOverdraftLimit(){ return overdraftLimit; }
    public void setOverdraftLimit(double l){ if (l < 0) throw new IllegalArgumentException("limit"); this.overdraftLimit = l; }
    @Override protected boolean canWithdraw(double amount) { return getBalance() - amount >= -overdraftLimit; }
}

/** Simple Factory demonstrating creational pattern */
class AccountFactory {
    private AccountFactory(){}
    public static Account create(AccountType type, Customer owner, double openingBalance) {
        switch (type) {
            case SAVINGS: return new SavingsAccount(owner, openingBalance, 0.06); // default 6%
            case CURRENT: return new CurrentAccount(owner, openingBalance, 10_000);
            default: throw new IllegalArgumentException("Unknown account type");
        }
    }
}

/** Bank aggregates customers and accounts */
class Bank implements Serializable {
    private final String name;
    private final Repository<Customer> customers = new Repository<>();
    private final Repository<Account> accounts = new Repository<>();

    public Bank(String name) { this.name = Objects.requireNonNull(name); }
    public String getName() { return name; }

    public Customer createCustomer(String name, String email) {
        Customer c = new Customer(name, email);
        customers.save(c);
        return c;
    }

    public Account openAccount(AccountType type, String customerId, double openingBalance) throws DomainException {
        Customer c = customers.find(customerId).orElseThrow(() -> new DomainException("Customer not found"));
        Account acc = AccountFactory.create(type, c, openingBalance);
        accounts.save(acc);
        return acc;
    }

    public Optional<Account> findAccount(String id){ return accounts.find(id); }
    public Optional<Customer> findCustomer(String id){ return customers.find(id); }
    public Collection<Account> allAccounts(){ return accounts.all(); }
    public Collection<Customer> allCustomers(){ return customers.all(); }

    public void applyMonthlyInterestToAllSavings(){
        for (Account a : accounts.all()) {
            if (a instanceof InterestBearing) {
                ((InterestBearing)a).applyMonthlyInterest(); // Polymorphism
            }
        }
    }
}

// ====== Utilities ======
class IdGenerator implements Serializable {
    private static final Map<String, Integer> counters = new HashMap<>();
    public static synchronized String next(String prefix) {
        int n = counters.getOrDefault(prefix, 0) + 1;
        counters.put(prefix, n);
        return prefix + String.format("%05d", n);
    }
}

// ====== Console UI Layer ======
class ConsoleApp {
    private final Bank bank;
    private final Scanner in = new Scanner(System.in);

    public ConsoleApp(Bank bank){ this.bank = bank; }

    public void run(){
        println("Welcome to " + bank.getName());
        help();
        while (true) {
            System.out.print("\n> ");
            String cmd = in.nextLine().trim().toLowerCase();
            try {
                switch (cmd) {
                    case "help": help(); break;
                    case "list customers": listCustomers(); break;
                    case "add customer": addCustomer(); break;
                    case "list accounts": listAccounts(); break;
                    case "open": openAccount(); break;
                    case "deposit": deposit(); break;
                    case "withdraw": withdraw(); break;
                    case "transfer": transfer(); break;
                    case "history": history(); break;
                    case "interest": applyInterest(); break;
                    case "exit": println("Goodbye!"); return;
                    default: println("Unknown command. Type 'help'.");
                }
            } catch (DomainException | IllegalArgumentException ex) {
                println("Error: " + ex.getMessage());
            } catch (Exception ex) {
                println("Unexpected error: " + ex);
            }
        }
    }

    private void help(){
        println("Commands:\n" +
               "  help             - show commands\n" +
               "  list customers   - list all customers\n" +
               "  add customer     - create a customer\n" +
               "  list accounts    - list all accounts\n" +
               "  open             - open an account\n" +
               "  deposit          - deposit into an account\n" +
               "  withdraw         - withdraw from an account\n" +
               "  transfer         - transfer between accounts\n" +
               "  history          - show account history\n" +
               "  interest         - apply monthly interest to savings\n" +
               "  exit             - quit");
    }

    private void listCustomers(){
        println("Customers:");
        bank.allCustomers().forEach(c -> println("  "+c));
    }

    private void addCustomer(){
        System.out.print("Name: "); String name = in.nextLine();
        System.out.print("Email: "); String email = in.nextLine();
        Customer c = bank.createCustomer(name, email);
        println("Created: "+c);
    }

    private void listAccounts(){
        println("Accounts:");
        bank.allAccounts().forEach(a -> println("  "+a));
    }

    private void openAccount() throws DomainException {
        System.out.print("Customer ID: "); String cid = in.nextLine();
        System.out.print("Type (SAVINGS/CURRENT): "); AccountType t = AccountType.valueOf(in.nextLine().trim().toUpperCase());
        System.out.print("Opening Balance: "); double ob = Double.parseDouble(in.nextLine());
        Account acc = bank.openAccount(t, cid, ob);
        println("Opened: "+acc);
    }

    private void deposit() throws DomainException {
        Account a = askAccount("Account ID: ");
        System.out.print("Amount: "); double amt = Double.parseDouble(in.nextLine());
        a.deposit(amt);
        println("New balance: "+a.getBalance());
    }

    private void withdraw() throws DomainException {
        Account a = askAccount("Account ID: ");
        System.out.print("Amount: "); double amt = Double.parseDouble(in.nextLine());
        a.withdraw(amt);
        println("New balance: "+a.getBalance());
    }

    private void transfer() throws DomainException {
        Account from = askAccount("From Account ID: ");
        Account to = askAccount("To Account ID: ");
        System.out.print("Amount: "); double amt = Double.parseDouble(in.nextLine());
        from.transferTo(to, amt);
        println("Transferred. Balances -> From: "+from.getBalance()+", To: "+to.getBalance());
    }

    private void history() throws DomainException {
        Account a = askAccount("Account ID: ");
        println("History for "+a.getId()+":");
        a.getHistory().forEach(t -> println("  "+t));
    }

    private void applyInterest(){
        bank.applyMonthlyInterestToAllSavings();
        println("Interest applied to all savings accounts.");
    }

    private Account askAccount(String prompt) throws DomainException {
        System.out.print(prompt); String id = in.nextLine();
        return bank.findAccount(id).orElseThrow(() -> new DomainException("Account not found"));
    }

    private void println(String s){ System.out.println(s); }
}

// ====== Seed data to try the app quickly ======
class SeedData {
    public static void seed(Bank bank) {
        Customer a = bank.createCustomer("Alice", "alice@example.com");
        Customer b = bank.createCustomer("Bob", "bob@example.com");
        try {
            Account a1 = bank.openAccount(AccountType.SAVINGS, a.getId(), 25_000);
            Account a2 = bank.openAccount(AccountType.CURRENT, a.getId(), 5_000);
            Account b1 = bank.openAccount(AccountType.SAVINGS, b.getId(), 15_000);
            // Sample transactions
            a1.deposit(2000);
            a2.withdraw(1000);
            a1.transferTo(b1, 1500);
        } catch (DomainException e) {
            System.err.println("Seeding failed: "+e.getMessage());
        }
    }
}
