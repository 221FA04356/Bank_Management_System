package com.bank.service;

import com.bank.model.*;
import com.bank.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class BankService {
    static final List<String> DEBITS = List.of("WITHDRAW", "TRANSFER_OUT");
    public static final List<String> CREDIT_TYPES = List.of("DEPOSIT", "TRANSFER_IN", "INTEREST", "LOAN", "GOAL_WITHDRAW");

    @Autowired CustomerRepo customers;
    @Autowired TxnRepo txns;
    @Autowired AuditRepo audits;
    @Autowired GoalRepo goals;
    @Autowired LoanRepo loans;
    @Autowired MoneyRequestRepo requests;
    @Autowired NotificationService notes;
    @Value("${app.otp-threshold:10000}") BigDecimal otpThreshold;
    @Value("${app.loan-rate:10}") BigDecimal loanRate;
    @Value("${app.interest-rate:3.5}") BigDecimal interestRate;

    public BigDecimal getOtpThreshold() { return otpThreshold; }
    public BigDecimal getLoanRate() { return loanRate; }

    private static String rs(BigDecimal a) { return String.format(Locale.ENGLISH, "Rs. %,.2f", a); }
    private static String acct(Customer c) { return "xxxxxx" + c.accountNumber.substring(c.accountNumber.length() - 4); }

    public void audit(String actor, String action, String detail) {
        Audit a = new Audit();
        a.actor = actor; a.action = action; a.detail = detail;
        audits.save(a);
    }

    private Customer lock(Long id) { return customers.lockById(id).orElseThrow(); }

    private void record(Customer c, String type, BigDecimal amt, String note) {
        Txn t = new Txn();
        t.customerId = c.id; t.type = type; t.amount = amt; t.balanceAfter = c.balance; t.note = note;
        txns.save(t);
    }

    // ---------- deposit / withdraw ----------
    @Transactional
    public void deposit(Long id, BigDecimal amt) {
        Customer c = lock(id);
        c.balance = c.balance.add(amt);
        record(c, "DEPOSIT", amt, "Cash deposit");
        notes.send(c, "TRANSACTION", "Deposit received",
                rs(amt) + " was deposited to account " + acct(c) + ".\nAvailable balance: " + rs(c.balance) + ".", true);
    }

    public String checkDebit(Customer c, BigDecimal amt) {
        if (amt == null || amt.signum() <= 0) return "Enter a valid amount.";
        if (c.balance.compareTo(amt) < 0) return "Insufficient balance.";
        BigDecimal used = txns.sumSince(c.id, DEBITS, LocalDate.now().atStartOfDay());
        if (used.add(amt).compareTo(c.dailyLimit) > 0)
            return "Daily limit exceeded. Used today: Rs. " + used + ", your limit: Rs. " + c.dailyLimit + ".";
        return null;
    }

    @Transactional
    public String withdraw(Long id, BigDecimal amt) {
        Customer c = lock(id);
        String err = checkDebit(c, amt);
        if (err != null) return err;
        c.balance = c.balance.subtract(amt);
        record(c, "WITHDRAW", amt, "Cash withdrawal");
        notes.send(c, "TRANSACTION", "Withdrawal made",
                rs(amt) + " was withdrawn from account " + acct(c) + ".\nAvailable balance: " + rs(c.balance) + ".", true);
        return null;
    }

    // ---------- OTP rule + fraud detection ----------
    public boolean needsOtp(Customer c, BigDecimal amt) {
        return amt.compareTo(otpThreshold) >= 0 || risky(c, amt);
    }

    public boolean risky(Customer c, BigDecimal amt) {
        String why = null;
        List<Txn> recent = txns.findTop20ByCustomerIdAndTypeInOrderByIdDesc(c.id, DEBITS);
        if (recent.size() >= 3) {
            BigDecimal avg = recent.stream().map(t -> t.amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(recent.size()), 2, RoundingMode.HALF_UP);
            if (amt.compareTo(new BigDecimal("5000")) >= 0 && amt.compareTo(avg.multiply(BigDecimal.valueOf(5))) > 0)
                why = "A debit of " + rs(amt) + " is more than 5 times your average debit of " + rs(avg);
        }
        if (why == null && txns.countByCustomerIdAndTypeInAndCreatedAtAfter(c.id, DEBITS, LocalDateTime.now().minusMinutes(10)) >= 5)
            why = "5 or more debits were attempted within 10 minutes";
        if (why == null) return false;
        audit(c.cardNumber, "FRAUD_ALERT", why);
        notes.send(c, "SECURITY", "Unusual activity detected",
                why + ".\nThis transaction needs OTP confirmation. If this was not you, please contact the bank immediately.", true);
        return true;
    }

    // ---------- transfer ----------
    public String transfer(Long fromId, String toAcc, BigDecimal amt) { return transfer(fromId, toAcc, amt, "Transfer"); }

    /** channel: "Transfer", "Scheduled transfer" or "Money request" - shown in both customers' emails. */
    @Transactional
    public String transfer(Long fromId, String toAcc, BigDecimal amt, String channel) {
        Customer t0 = customers.findByAccountNumber(toAcc).orElse(null);
        if (t0 == null || "ADMIN".equals(t0.role)) return "Beneficiary account not found.";
        if (t0.id.equals(fromId)) return "You cannot transfer to your own account.";
        Customer first = lock(Math.min(fromId, t0.id)), second = lock(Math.max(fromId, t0.id));
        Customer from = first.id.equals(fromId) ? first : second;
        Customer to = (from == first) ? second : first;
        String err = checkDebit(from, amt);
        if (err != null) return err;
        from.balance = from.balance.subtract(amt);
        to.balance = to.balance.add(amt);
        record(from, "TRANSFER_OUT", amt, channel + " to a/c " + to.accountNumber + " (" + to.name + ")");
        record(to, "TRANSFER_IN", amt, channel + " from a/c " + from.accountNumber + " (" + from.name + ")");
        audit(from.cardNumber, "TRANSFER", channel + ": Rs. " + amt + " to " + to.accountNumber);
        notes.send(from, "TRANSACTION", channel + ": " + rs(amt) + " sent",
                rs(amt) + " was debited from account " + acct(from) + " and sent to " + to.name + " (a/c " + acct(to)
                + ").\nType: " + channel + "\nAvailable balance: " + rs(from.balance) + ".", true);
        notes.send(to, "TRANSACTION", channel + ": " + rs(amt) + " received",
                rs(amt) + " was credited to account " + acct(to) + " by " + from.name + " (a/c " + acct(from)
                + ").\nType: " + channel + "\nAvailable balance: " + rs(to.balance) + ".", true);
        return null;
    }

    // ---------- money requests ----------
    @Transactional
    public MoneyRequest createRequest(Customer req, Customer payer, BigDecimal amt, String note) {
        MoneyRequest r = new MoneyRequest();
        r.requesterId = req.id; r.payerId = payer.id; r.amount = amt; r.note = note;
        requests.save(r);
        String n = note.isBlank() ? "" : "\nNote: " + note;
        notes.send(req, "REQUEST", "Money request sent",
                "You requested " + rs(amt) + " from " + payer.name + " (a/c " + acct(payer) + ")." + n, true);
        notes.send(payer, "REQUEST", "Money request received",
                req.name + " (a/c " + acct(req) + ") has requested " + rs(amt) + " from you." + n
                + "\nOpen JavaBank > Requests to approve or decline.", true);
        return r;
    }

    /** action: "decline" (by the payer) or "cancel" (by the requester). @return error or null */
    @Transactional
    public String respond(Long id, Long actor, String action) {
        MoneyRequest r = requests.findById(id).orElse(null);
        if (r == null || !"PENDING".equals(r.status)) return "This request is no longer pending.";
        boolean decline = "decline".equals(action);
        if (decline ? !r.payerId.equals(actor) : !r.requesterId.equals(actor)) return "You cannot do that.";
        r.status = decline ? "DECLINED" : "CANCELLED";
        requests.save(r);
        Customer rq = customers.findById(r.requesterId).orElseThrow(), py = customers.findById(r.payerId).orElseThrow();
        String what = rs(r.amount);
        if (decline) {
            notes.send(rq, "REQUEST", "Money request declined", py.name + " declined your request for " + what + ".", true);
            notes.send(py, "REQUEST", "You declined a request", "You declined the request for " + what + " from " + rq.name + ".", false);
        } else {
            notes.send(rq, "REQUEST", "Money request cancelled", "You cancelled your request for " + what + " from " + py.name + ".", false);
            notes.send(py, "REQUEST", "Money request cancelled", rq.name + " cancelled their request for " + what + ".", true);
        }
        return null;
    }

    @Transactional
    public void markPaid(Long id) {
        MoneyRequest r = requests.findById(id).orElse(null);
        if (r == null || !"PENDING".equals(r.status)) return;
        r.status = "PAID";
        requests.save(r);
        Customer rq = customers.findById(r.requesterId).orElseThrow(), py = customers.findById(r.payerId).orElseThrow();
        notes.send(rq, "REQUEST", "Your request was paid", py.name + " paid your request for " + rs(r.amount) + ".", false);
        notes.send(py, "REQUEST", "Request paid", "You paid " + rq.name + "'s request for " + rs(r.amount) + ".", false);
    }

    // ---------- savings goals ----------
    @Transactional
    public String goalMove(Long uid, Long goalId, BigDecimal amt, boolean deposit) {
        Goal g = goals.findById(goalId).orElse(null);
        if (g == null || !g.ownerId.equals(uid)) return "Goal not found.";
        if (amt == null) return "Enter a valid amount.";
        Customer c = lock(uid);
        if (deposit) {
            if (c.balance.compareTo(amt) < 0) return "Insufficient balance.";
            c.balance = c.balance.subtract(amt);
            g.saved = g.saved.add(amt);
            record(c, "GOAL_DEPOSIT", amt, "Saved to goal: " + g.name);
        } else {
            if (g.saved.compareTo(amt) < 0) return "Not enough saved in this goal.";
            g.saved = g.saved.subtract(amt);
            c.balance = c.balance.add(amt);
            record(c, "GOAL_WITHDRAW", amt, "Taken from goal: " + g.name);
        }
        goals.save(g);
        return null;
    }

    // ---------- interest ----------
    @Transactional
    public int creditInterest() {
        int n = 0;
        for (Customer c : customers.findByRoleAndAccountTypeIgnoreCase("USER", "Savings")) {
            BigDecimal i = c.balance.multiply(interestRate).divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
            if (i.signum() > 0) {
                Customer l = lock(c.id);
                l.balance = l.balance.add(i);
                record(l, "INTEREST", i, "Monthly interest @ " + interestRate + "% p.a.");
                notes.send(l, "TRANSACTION", "Interest credited",
                        rs(i) + " interest was credited to account " + acct(l) + ".\nAvailable balance: " + rs(l.balance) + ".", true);
                n++;
            }
        }
        audit("SYSTEM", "INTEREST", "Credited to " + n + " savings accounts");
        return n;
    }

    // ---------- loans ----------
    public static BigDecimal emi(BigDecimal principal, BigDecimal annualPct, int months) {
        double r = annualPct.doubleValue() / 1200.0, p = principal.doubleValue();
        double e = r == 0 ? p / months : p * r * Math.pow(1 + r, months) / (Math.pow(1 + r, months) - 1);
        return BigDecimal.valueOf(e).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Loan applyLoan(Long uid, BigDecimal principal, int months) {
        Loan l = new Loan();
        l.ownerId = uid; l.principal = principal; l.rate = loanRate; l.months = months;
        l.emi = emi(principal, loanRate, months); l.status = "PENDING";
        loans.save(l);
        Customer c = customers.findById(uid).orElseThrow();
        notes.send(c, "LOAN", "Loan application received",
                "Your application #" + l.id + " for " + rs(principal) + " over " + months + " months (EMI " + rs(l.emi)
                + " at " + loanRate + "% p.a.) is under review.", true);
        notes.sendAdmins("New loan application", c.name + " applied for " + rs(principal) + " (" + months + " months).");
        return l;
    }

    @Transactional
    public String decideLoan(Long loanId, boolean approve, String admin) {
        Loan l = loans.findById(loanId).orElse(null);
        if (l == null || !"PENDING".equals(l.status)) return "Loan is not pending.";
        l.status = approve ? "APPROVED" : "REJECTED";
        loans.save(l);
        Customer c = lock(l.ownerId);
        if (approve) {
            c.balance = c.balance.add(l.principal);
            record(c, "LOAN", l.principal, "Loan #" + l.id + " disbursed (EMI Rs. " + l.emi + " x " + l.months + ")");
            notes.send(c, "LOAN", "Loan approved",
                    "Your loan #" + l.id + " of " + rs(l.principal) + " is approved and credited to account " + acct(c)
                    + ".\nEMI: " + rs(l.emi) + " x " + l.months + " months.\nAvailable balance: " + rs(c.balance) + ".", true);
        } else {
            notes.send(c, "LOAN", "Loan rejected", "Your loan application #" + l.id + " for " + rs(l.principal) + " was not approved.", true);
        }
        audit(admin, "LOAN_" + l.status, "loan #" + l.id + " for customer " + c.id);
        return null;
    }
}
