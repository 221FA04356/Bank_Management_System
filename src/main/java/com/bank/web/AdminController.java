package com.bank.web;

import com.bank.model.Customer;
import com.bank.repo.*;
import com.bank.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController extends Base {
    @Autowired TxnRepo txns;
    @Autowired AuditRepo audits;
    @Autowired LoanRepo loans;
    @Autowired BankService bank;
    @Autowired Jobs jobs;
    @Autowired NotificationService notes;

    @GetMapping
    public String home(Model m) {
        List<Customer> list = customers.findByRoleOrderByIdDesc("USER");
        m.addAttribute("customers", list);
        m.addAttribute("names", list.stream().collect(Collectors.toMap(c -> c.id, c -> c.name)));
        m.addAttribute("total", customers.totalBalance());
        m.addAttribute("pending", loans.findByStatusOrderByIdAsc("PENDING"));
        m.addAttribute("recent", txns.findTop15ByOrderByIdDesc());
        m.addAttribute("logs", audits.findTop30ByOrderByIdDesc());
        return "admin";
    }

    private String admin(HttpSession s) { return me(s).cardNumber; }

    @PostMapping("/lock/{id}")
    public String lock(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
        customers.findById(id).filter(c -> "USER".equals(c.role)).ifPresent(c -> {
            c.locked = true; customers.save(c);
            bank.audit(admin(s), "ADMIN_LOCK", "customer " + c.id);
            notes.send(c, "SECURITY", "Card locked by the bank", "Your card was locked by the bank. Please contact support.", true);
        });
        return "redirect:/admin";
    }

    @PostMapping("/unlock/{id}")
    public String unlock(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
        customers.findById(id).filter(c -> "USER".equals(c.role)).ifPresent(c -> {
            c.locked = false; c.failedAttempts = 0; customers.save(c);
            bank.audit(admin(s), "ADMIN_UNLOCK", "customer " + c.id);
            notes.send(c, "SECURITY", "Card unlocked by the bank", "Your card was unlocked by the bank. You can sign in now.", true);
        });
        return "redirect:/admin";
    }

    @PostMapping("/loans/{id}/{decision}")
    public String loan(@PathVariable Long id, @PathVariable String decision, HttpSession s, RedirectAttributes ra) {
        String err = bank.decideLoan(id, "approve".equals(decision), admin(s));
        if (err != null) ra.addFlashAttribute("err", err); else ra.addFlashAttribute("msg", "Loan " + decision + "d.");
        return "redirect:/admin";
    }

    @PostMapping("/run-interest")
    public String interest(HttpSession s, RedirectAttributes ra) {
        ra.addFlashAttribute("msg", "Interest credited to " + bank.creditInterest() + " savings accounts.");
        return "redirect:/admin";
    }

    @PostMapping("/run-scheduled")
    public String scheduled(HttpSession s, RedirectAttributes ra) {
        ra.addFlashAttribute("msg", jobs.runStandingOrders() + " scheduled transfers executed.");
        return "redirect:/admin";
    }
}
