package com.bank.web;

import com.bank.model.*;
import com.bank.repo.*;
import com.bank.service.*;
import com.bank.service.OtpService.Pending;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Controller
public class AccountController extends Base {
    @Autowired BankService bank;
    @Autowired OtpService otp;
    @Autowired BeneficiaryRepo bens;
    @Autowired TxnRepo txns;
    @Autowired NotificationService notes;

    private static String mask(String e) { return e.replaceAll("(?<=.).(?=[^@]*@)", "*"); }

    @GetMapping("/dashboard") public String dashboard() { return "dashboard"; }

    @GetMapping("/balance") public String balance() { return "balance"; }

    @GetMapping("/ministatement")
    public String mini(HttpSession s, Model m) {
        m.addAttribute("list", txns.findTop10ByCustomerIdOrderByIdDesc(uid(s)));
        return "ministatement";
    }

    // ---------- deposit ----------
    @GetMapping("/deposit")
    public String depositForm(Model m) { m.addAttribute("title", "Deposit"); m.addAttribute("action", "/deposit"); return "amount"; }

    @PostMapping("/deposit")
    public String deposit(@RequestParam String amount, HttpSession s, RedirectAttributes ra) {
        BigDecimal a = amount(amount);
        if (a == null || a.compareTo(new BigDecimal("1000000")) > 0) {
            ra.addFlashAttribute("err", "Enter a valid amount (maximum Rs. 10,00,000 per deposit).");
            return "redirect:/deposit";
        }
        bank.deposit(uid(s), a);
        ra.addFlashAttribute("msg", "Deposited Rs. " + a + " successfully.");
        return "redirect:/dashboard";
    }

    // ---------- withdraw + fast cash ----------
    @GetMapping("/withdraw")
    public String withdrawForm(Model m) { m.addAttribute("title", "Withdraw"); m.addAttribute("action", "/withdraw"); return "amount"; }

    @GetMapping("/fastcash") public String fast() { return "fastcash"; }

    @PostMapping("/withdraw")
    public String withdraw(@RequestParam String amount, @RequestParam(required = false) String from, HttpSession s, RedirectAttributes ra) {
        String back = "fast".equals(from) ? "redirect:/fastcash" : "redirect:/withdraw";
        Customer c = me(s);
        BigDecimal a = amount(amount);
        String err = a == null ? "Enter a valid amount." : bank.checkDebit(c, a);
        if (err != null) { ra.addFlashAttribute("err", err); return back; }
        if (bank.needsOtp(c, a)) {
            s.setAttribute("pending", otp.issue(c, "WITHDRAW", a, null));
            ra.addFlashAttribute("msg", "OTP sent to " + mask(c.email) + " to confirm this withdrawal.");
            return "redirect:/otp";
        }
        err = bank.withdraw(c.id, a);
        if (err != null) { ra.addFlashAttribute("err", err); return back; }
        ra.addFlashAttribute("msg", "Please collect your cash: Rs. " + a);
        return "redirect:/dashboard";
    }

    // ---------- OTP confirmation (withdraw / transfer) ----------
    @GetMapping("/otp")
    public String otpPage(HttpSession s, Model m) {
        Pending p = (Pending) s.getAttribute("pending");
        if (p == null) return "redirect:/dashboard";
        m.addAttribute("p", p);
        m.addAttribute("left", Math.max(0, Duration.between(Instant.now(), p.expires).getSeconds()));
        return "otp";
    }

    @PostMapping("/otp")
    public String otpVerify(@RequestParam String code, HttpSession s, RedirectAttributes ra) {
        Pending p = (Pending) s.getAttribute("pending");
        if (p == null) return "redirect:/dashboard";
        String err = otp.check(p, code);
        if (err != null) {
            ra.addFlashAttribute("err", err);
            if (otp.dead(p)) { s.removeAttribute("pending"); return "redirect:/dashboard?timeout"; }
            return "redirect:/otp";
        }
        s.removeAttribute("pending");
        err = "WITHDRAW".equals(p.purpose)
                ? bank.withdraw(uid(s), p.amount)
                : bank.transfer(uid(s), p.to, p.amount, p.channel == null ? "Transfer" : p.channel);
        if (err != null) ra.addFlashAttribute("err", err);
        else {
            if (p.ref != null) bank.markPaid(p.ref);
            ra.addFlashAttribute("msg", "Transaction of Rs. " + p.amount + " completed.");
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/otp/cancel")
    public String otpCancel(HttpSession s) {
        Pending p = (Pending) s.getAttribute("pending");
        s.removeAttribute("pending");
        return p != null && "WITHDRAW".equals(p.purpose) ? "redirect:/withdraw" : "redirect:/transfer";
    }

    // ---------- transfer: step 1 (details) -> step 2 (review, locked, 5-minute window) -> confirm ----------
    @GetMapping("/transfer")
    public String transferForm(@RequestParam(required = false) String to, @RequestParam(required = false) String back, HttpSession s, Model m) {
        Draft d = (Draft) s.getAttribute("tdraft");
        if ("1".equals(back) && d != null) { to = d.to; m.addAttribute("amt", d.amount); }
        m.addAttribute("to", to);
        m.addAttribute("bens", bens.findByOwnerId(uid(s)));
        return "transfer";
    }

    @PostMapping("/transfer/review")
    public String review(@RequestParam String to, @RequestParam String amount, HttpSession s, RedirectAttributes ra) {
        Customer c = me(s);
        to = to.trim();
        BigDecimal a = amount(amount);
        Customer t = customers.findByAccountNumber(to).orElse(null);
        String err = a == null ? "Enter a valid amount."
                : (t == null || "ADMIN".equals(t.role) || t.id.equals(c.id)) ? "Invalid beneficiary account number."
                : bank.checkDebit(c, a);
        if (err != null) { ra.addFlashAttribute("err", err); return "redirect:/transfer"; }
        Draft d = new Draft();
        d.to = t.accountNumber; d.name = maskName(t.name); d.amount = a;
        s.setAttribute("tdraft", d);
        return "redirect:/transfer/review";
    }

    @GetMapping("/transfer/review")
    public String reviewPage(HttpSession s, Model m) {
        Draft d = (Draft) s.getAttribute("tdraft");
        if (d == null || d.left() <= 0) { s.removeAttribute("tdraft"); return "redirect:/dashboard?timeout"; }
        m.addAttribute("d", d);
        m.addAttribute("left", d.left());
        return "transfer_review";
    }

    @PostMapping("/transfer/confirm")
    public String confirm(HttpSession s, RedirectAttributes ra) {
        Draft d = (Draft) s.getAttribute("tdraft");
        if (d == null || d.left() <= 0) { s.removeAttribute("tdraft"); return "redirect:/dashboard?timeout"; }
        s.removeAttribute("tdraft");
        Customer c = me(s);
        String err = bank.checkDebit(c, d.amount);
        if (err != null) { ra.addFlashAttribute("err", err); return "redirect:/transfer"; }
        if (bank.needsOtp(c, d.amount)) {
            Pending p = otp.issue(c, "TRANSFER", d.amount, d.to);
            p.ref = d.requestId; p.channel = d.channel;
            s.setAttribute("pending", p);
            ra.addFlashAttribute("msg", "OTP sent to " + mask(c.email) + " to confirm this transfer.");
            return "redirect:/otp";
        }
        err = bank.transfer(c.id, d.to, d.amount, d.channel);
        if (err != null) { ra.addFlashAttribute("err", err); return "redirect:/transfer"; }
        if (d.requestId != null) bank.markPaid(d.requestId);
        ra.addFlashAttribute("msg", "Transferred Rs. " + d.amount + " successfully.");
        return "redirect:/dashboard";
    }

    // ---------- beneficiaries: verify account -> locked confirmation (5-minute window) -> save ----------
    @GetMapping("/beneficiaries")
    public String beneficiaries(HttpSession s, Model m) {
        s.removeAttribute("bdraft");
        m.addAttribute("bens", bens.findByOwnerId(uid(s)));
        return "beneficiaries";
    }

    @PostMapping("/beneficiaries/lookup")
    public String lookup(@RequestParam String accountNumber, HttpSession s, RedirectAttributes ra) {
        Customer t = customers.findByAccountNumber(accountNumber.trim()).orElse(null);
        if (t == null || "ADMIN".equals(t.role) || t.id.equals(uid(s))) {
            ra.addFlashAttribute("err", "No customer account found with that number (you cannot add your own account).");
            return "redirect:/beneficiaries";
        }
        if (bens.findByOwnerId(uid(s)).stream().anyMatch(b -> b.accountNumber.equals(t.accountNumber))) {
            ra.addFlashAttribute("err", "This account is already in your beneficiary list.");
            return "redirect:/beneficiaries";
        }
        Draft d = new Draft();
        d.to = t.accountNumber; d.name = maskName(t.name);
        s.setAttribute("bdraft", d);
        return "redirect:/beneficiaries/confirm";
    }

    @GetMapping("/beneficiaries/confirm")
    public String confirmPage(HttpSession s, Model m, RedirectAttributes ra) {
        Draft d = (Draft) s.getAttribute("bdraft");
        if (d == null || d.left() <= 0) {
            s.removeAttribute("bdraft");
            ra.addFlashAttribute("err", "That step timed out after 5 minutes. Please start again.");
            return "redirect:/beneficiaries";
        }
        m.addAttribute("d", d);
        m.addAttribute("left", d.left());
        return "beneficiary_confirm";
    }

    @PostMapping("/beneficiaries/confirm")
    public String saveBen(@RequestParam String nickname, HttpSession s, RedirectAttributes ra) {
        Draft d = (Draft) s.getAttribute("bdraft");
        if (d == null || d.left() <= 0) {
            s.removeAttribute("bdraft");
            ra.addFlashAttribute("err", "That step timed out after 5 minutes. Please start again.");
            return "redirect:/beneficiaries";
        }
        if (nickname.isBlank()) { ra.addFlashAttribute("err", "Enter a nickname."); return "redirect:/beneficiaries/confirm"; }
        Beneficiary b = new Beneficiary();
        b.ownerId = uid(s);
        b.name = nickname.trim().length() > 40 ? nickname.trim().substring(0, 40) : nickname.trim();
        b.accountNumber = d.to;               // taken from the server-side draft, never from the form
        bens.save(b);
        s.removeAttribute("bdraft");
        ra.addFlashAttribute("msg", "Beneficiary saved.");
        return "redirect:/beneficiaries";
    }

    @PostMapping("/beneficiaries/{id}/delete")
    public String delBen(@PathVariable Long id, HttpSession s) {
        bens.findById(id).filter(b -> b.ownerId.equals(uid(s))).ifPresent(bens::delete);
        return "redirect:/beneficiaries";
    }
}
