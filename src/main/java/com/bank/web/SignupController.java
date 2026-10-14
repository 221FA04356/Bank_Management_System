package com.bank.web;

import com.bank.model.Customer;
import com.bank.service.MailService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.SecureRandom;
import java.util.*;

@Controller
@RequestMapping("/signup")
public class SignupController extends Base {
    @Autowired BCryptPasswordEncoder enc;
    @Autowired MailService mail;
    private final SecureRandom rnd = new SecureRandom();

    private static final List<String> S1 = List.of("name", "fatherName", "dob", "gender", "email", "marital", "address", "city", "state", "pincode");
    private static final List<String> S2 = List.of("religion", "category", "income", "education", "occupation", "pan", "aadhar", "seniorCitizen", "existingAccount");

    @SuppressWarnings("unchecked")
    private Map<String, String> data(HttpSession s) {
        Map<String, String> m = (Map<String, String>) s.getAttribute("signup");
        if (m == null) { m = new HashMap<>(); s.setAttribute("signup", m); }
        return m;
    }

    private void copy(Map<String, String> from, Map<String, String> to, List<String> keys) {
        for (String k : keys) to.put(k, from.getOrDefault(k, "").trim());
    }

    /** One account per person: email, PAN and Aadhar must be unique, and so must name + date of birth. */
    private String duplicate(String name, String dob, String email, String pan, String aadhar) {
        if (email != null && customers.existsByEmailIgnoreCase(email)) return "This email is already registered. Only one account per person is allowed.";
        if (pan != null && customers.existsByPan(pan)) return "An account already exists for this PAN number.";
        if (aadhar != null && customers.existsByAadhar(aadhar)) return "An account already exists for this Aadhar number.";
        if (name != null && customers.existsByNameIgnoreCaseAndDob(name, dob)) return "An account already exists for a customer with this name and date of birth.";
        return null;
    }

    // ----- Page 1: personal details -----
    @GetMapping("/1")
    public String p1(HttpSession s, Model m) { m.addAttribute("d", data(s)); return "signup1"; }

    @PostMapping("/1")
    public String p1post(@RequestParam Map<String, String> p, HttpSession s, RedirectAttributes ra) {
        String name = p.getOrDefault("name", "").trim(), dob = p.getOrDefault("dob", "").trim(), email = p.getOrDefault("email", "").trim();
        String err = null;
        if (name.isBlank() || dob.isBlank()) err = "Name and date of birth are required.";
        else if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) err = "Enter a valid email address (OTPs and alerts are sent here).";
        else if (!p.getOrDefault("pincode", "").trim().matches("\\d{6}")) err = "Pincode must be 6 digits.";
        else err = duplicate(name, dob, email, null, null);
        copy(p, data(s), S1);   // keep what was typed so Back/forward never loses it
        if (err != null) { ra.addFlashAttribute("err", err); return "redirect:/signup/1"; }
        return "redirect:/signup/2";
    }

    // ----- Page 2: additional details -----
    @GetMapping("/2")
    public String p2(HttpSession s, Model m) {
        if (!data(s).containsKey("name") || data(s).get("name").isBlank()) return "redirect:/signup/1";
        m.addAttribute("d", data(s));
        return "signup2";
    }

    @PostMapping("/2")
    public String p2post(@RequestParam Map<String, String> p, HttpSession s, RedirectAttributes ra) {
        if (!data(s).containsKey("name")) return "redirect:/signup/1";
        String aadhar = p.getOrDefault("aadhar", "").trim(), pan = p.getOrDefault("pan", "").trim().toUpperCase();
        p.put("pan", pan);
        copy(p, data(s), S2);
        String err = null;
        if (!aadhar.matches("\\d{12}")) err = "Aadhar must be 12 digits.";
        else if (!pan.matches("[A-Z]{5}\\d{4}[A-Z]")) err = "PAN format is invalid (e.g. ABCDE1234F).";
        else err = duplicate(null, null, null, pan, aadhar);
        if (err != null) { ra.addFlashAttribute("err", err); return "redirect:/signup/2"; }
        data(s).put("step2ok", "1");
        return "redirect:/signup/3";
    }

    // ----- Page 3: account type, services, PIN -----
    @GetMapping("/3")
    public String p3(HttpSession s, Model m) {
        if (!"1".equals(data(s).get("step2ok"))) return "redirect:/signup/1";
        m.addAttribute("d", data(s));
        return "signup3";
    }

    private static String last(String s, int n) { return s.substring(Math.max(0, s.length() - n)); }

    @PostMapping("/3")
    public String p3post(@RequestParam String accountType, @RequestParam(required = false) List<String> services,
                         @RequestParam String pin, @RequestParam String pin2, HttpSession s, RedirectAttributes ra) {
        Map<String, String> m = data(s);
        if (!"1".equals(m.get("step2ok"))) return "redirect:/signup/1";
        if (!pin4(pin) || !pin.equals(pin2)) { ra.addFlashAttribute("err", "PIN must be exactly 4 digits and both entries must match."); return "redirect:/signup/3"; }
        if (!List.of("Savings", "Current", "Fixed Deposit", "Recurring Deposit").contains(accountType)) return "redirect:/signup/3";
        String dup = duplicate(m.get("name"), m.get("dob"), m.get("email"), m.get("pan"), m.get("aadhar"));
        if (dup != null) { ra.addFlashAttribute("err", dup); return "redirect:/signup/1"; }

        Customer c = new Customer();
        c.name = m.get("name"); c.fatherName = m.get("fatherName"); c.dob = m.get("dob"); c.gender = m.get("gender");
        c.email = m.get("email"); c.marital = m.get("marital"); c.address = m.get("address"); c.city = m.get("city");
        c.state = m.get("state"); c.pincode = m.get("pincode"); c.religion = m.get("religion"); c.category = m.get("category");
        c.income = m.get("income"); c.education = m.get("education"); c.occupation = m.get("occupation");
        c.pan = m.get("pan"); c.aadhar = m.get("aadhar"); c.seniorCitizen = m.get("seniorCitizen"); c.existingAccount = m.get("existingAccount");
        c.accountType = accountType;
        c.services = services == null ? "" : String.join(", ", services);
        do { c.cardNumber = "5040" + digits(12); } while (customers.existsByCardNumber(c.cardNumber));
        do { c.accountNumber = "10" + digits(10); } while (customers.existsByAccountNumber(c.accountNumber));
        c.pinHash = enc.encode(pin);
        try {
            customers.save(c);
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("err", "An account already exists with these identity details. Only one account per person is allowed.");
            return "redirect:/signup/1";
        }
        s.removeAttribute("signup");

        mail.send(c.email, "Welcome to JavaBank - your account details",
                "Hello " + c.name + ",\n\nWelcome to JavaBank! Your account is open. Here are the details you submitted:\n\n"
                + "ACCOUNT\n  Type            : " + c.accountType + "\n  Card number     : " + c.cardNumber
                + "\n  Account number  : " + c.accountNumber + "\n  Services        : " + (c.services.isBlank() ? "None" : c.services)
                + "\n\nPERSONAL DETAILS\n  Name            : " + c.name + "\n  Father's name   : " + c.fatherName
                + "\n  Date of birth   : " + c.dob + "\n  Gender          : " + c.gender + "\n  Marital status  : " + c.marital
                + "\n  Email           : " + c.email + "\n  Address         : " + c.address + ", " + c.city + ", " + c.state + " - " + c.pincode
                + "\n\nADDITIONAL DETAILS\n  Religion        : " + c.religion + "\n  Category        : " + c.category
                + "\n  Income          : " + c.income + "\n  Education       : " + c.education + "\n  Occupation      : " + c.occupation
                + "\n  PAN             : XXXXXX" + last(c.pan, 4) + "\n  Aadhar          : XXXXXXXX" + last(c.aadhar, 4)
                + "\n  Senior citizen  : " + c.seniorCitizen + "\n  Existing account: " + c.existingAccount
                + "\n\nYour PIN is never emailed. If you did not open this account, contact the bank immediately.\n\n- JavaBank");
        ra.addFlashAttribute("card", c.cardNumber);
        ra.addFlashAttribute("acct", c.accountNumber);
        return "redirect:/signup/done";
    }

    @GetMapping("/done") public String done() { return "signup_done"; }

    private String digits(int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(rnd.nextInt(10));
        return b.toString();
    }
}
