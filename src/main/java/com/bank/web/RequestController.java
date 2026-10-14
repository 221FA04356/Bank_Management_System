package com.bank.web;

import com.bank.model.*;
import com.bank.repo.MoneyRequestRepo;
import com.bank.service.BankService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

/** Person A asks Person B for money; B approves (goes through the normal transfer review / OTP rules) or declines. */
@Controller
@RequestMapping("/requests")
public class RequestController extends Base {
    @Autowired MoneyRequestRepo reqs;
    @Autowired BankService bank;

    @GetMapping
    public String page(HttpSession s, Model m) {
        Long id = uid(s);
        List<MoneyRequest> in = reqs.findByPayerIdOrderByIdDesc(id), out = reqs.findByRequesterIdOrderByIdDesc(id);
        Set<Long> ids = new HashSet<>();
        in.forEach(r -> ids.add(r.requesterId));
        out.forEach(r -> ids.add(r.payerId));
        Map<Long, String> names = new HashMap<>();
        customers.findAllById(ids).forEach(c -> names.put(c.id, c.name));
        m.addAttribute("incoming", in);
        m.addAttribute("outgoing", out);
        m.addAttribute("names", names);
        return "requests";
    }

    @PostMapping
    public String create(@RequestParam String to, @RequestParam String amount, @RequestParam(defaultValue = "") String note,
                         HttpSession s, RedirectAttributes ra) {
        Customer me = me(s);
        Customer payer = customers.findByAccountNumber(to.trim()).orElse(null);
        BigDecimal a = amount(amount);
        if (payer == null || "ADMIN".equals(payer.role) || payer.id.equals(me.id) || a == null) {
            ra.addFlashAttribute("err", "Enter a valid account number of another customer and a valid amount.");
        } else {
            note = note.trim();
            bank.createRequest(me, payer, a, note.length() > 100 ? note.substring(0, 100) : note);
            ra.addFlashAttribute("msg", "Request sent. " + maskName(payer.name) + " has been notified.");
        }
        return "redirect:/requests";
    }

    @PostMapping("/{id}/approve")
    public String approve(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
        MoneyRequest r = reqs.findById(id).orElse(null);
        if (r == null || !r.payerId.equals(uid(s)) || !"PENDING".equals(r.status)) {
            ra.addFlashAttribute("err", "This request is no longer pending.");
            return "redirect:/requests";
        }
        Customer rq = customers.findById(r.requesterId).orElseThrow();
        Draft d = new Draft();
        d.to = rq.accountNumber; d.name = maskName(rq.name); d.amount = r.amount;
        d.channel = "Money request"; d.requestId = r.id;
        s.setAttribute("tdraft", d);
        return "redirect:/transfer/review";
    }

    @PostMapping("/{id}/{action}")
    public String respond(@PathVariable Long id, @PathVariable String action, HttpSession s, RedirectAttributes ra) {
        if (!List.of("decline", "cancel").contains(action)) return "redirect:/requests";
        String err = bank.respond(id, uid(s), action);
        if (err != null) ra.addFlashAttribute("err", err);
        return "redirect:/requests";
    }
}
