package com.bank.web;

import com.bank.model.Customer;
import com.bank.service.*;
import com.bank.service.OtpService.Pending;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController extends Base {
    @Autowired BCryptPasswordEncoder enc;
    @Autowired BankService bank;
    @Autowired OtpService otp;
    @Autowired NotificationService notes;

    @GetMapping("/")
    public String home(HttpSession s) {
        return s.getAttribute("uid") == null ? "redirect:/login" : "redirect:/dashboard";
    }

    @GetMapping("/login") public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String login(@RequestParam String card, @RequestParam String pin, HttpServletRequest req, RedirectAttributes ra) {
        Customer c = customers.findByCardNumber(card.trim()).orElse(null);
        if (c == null) { ra.addFlashAttribute("err", "Invalid card number or PIN."); return "redirect:/login"; }
        if (c.locked) { ra.addFlashAttribute("err", "This card is locked after 3 wrong PINs. Use \"Unlock card\" below."); return "redirect:/login"; }
        if (!enc.matches(pin, c.pinHash)) {
            c.failedAttempts++;
            if (c.failedAttempts >= 3) {
                c.locked = true;
                bank.audit(c.cardNumber, "CARD_LOCKED", "3 wrong PIN attempts");
                customers.save(c);
                notes.send(c, "SECURITY", "Card locked",
                        "Your card ending " + c.cardNumber.substring(12) + " was locked after 3 wrong PIN attempts.\n"
                        + "Unlock it with an email OTP on the sign-in page, or contact the bank. If this was not you, please tell us.", true);
                ra.addFlashAttribute("err", "Card locked after 3 wrong PINs. Use \"Unlock card\" to unlock with an email OTP.");
            } else {
                customers.save(c);
                ra.addFlashAttribute("err", "Invalid card number or PIN. Attempts left: " + (3 - c.failedAttempts));
            }
            return "redirect:/login";
        }
        c.failedAttempts = 0;
        customers.save(c);
        req.getSession(true);
        req.changeSessionId();
        HttpSession s = req.getSession();
        s.setAttribute("uid", c.id);
        s.setAttribute("role", c.role);
        s.setAttribute("loginAt", System.currentTimeMillis());
        bank.audit(c.cardNumber, "LOGIN", "ok");
        String next = (String) s.getAttribute("next");
        s.removeAttribute("next");
        if ("ADMIN".equals(c.role)) return "redirect:/admin";
        return "redirect:" + (next != null && next.startsWith("/") && !next.startsWith("//") ? next : "/dashboard");
    }

    @PostMapping("/logout")
    public String logout(HttpSession s) { s.invalidate(); return "redirect:/login"; }

    // ---------- unlock a locked card with an email OTP ----------
    @GetMapping("/unlock") public String unlockPage(Model m) { return "unlock"; }

    @PostMapping("/unlock/send")
    public String unlockSend(@RequestParam String card, HttpSession s, RedirectAttributes ra) {
        Customer c = customers.findByCardNumber(card.trim()).orElse(null);
        if (c != null && c.locked) s.setAttribute("unlockPending", otp.issue(c, "UNLOCK", null, null));
        else s.removeAttribute("unlockPending");
        ra.addFlashAttribute("msg", "If the card exists and is locked, an OTP has been emailed to the registered address.");
        return "redirect:/unlock/verify";
    }

    @GetMapping("/unlock/verify")
    public String unlockVerifyPage(Model m) { m.addAttribute("verify", true); return "unlock"; }

    @PostMapping("/unlock/verify")
    public String unlockVerify(@RequestParam String code, HttpSession s, RedirectAttributes ra) {
        Pending p = (Pending) s.getAttribute("unlockPending");
        if (p == null) { ra.addFlashAttribute("err", "Invalid or expired OTP."); return "redirect:/unlock"; }
        String err = otp.check(p, code);
        if (err != null) {
            if (otp.dead(p)) s.removeAttribute("unlockPending");
            ra.addFlashAttribute("err", err);
            return otp.dead(p) ? "redirect:/unlock" : "redirect:/unlock/verify";
        }
        Customer c = customers.findById(p.customerId).orElseThrow();
        c.locked = false; c.failedAttempts = 0;
        customers.save(c);
        s.removeAttribute("unlockPending");
        bank.audit(c.cardNumber, "CARD_UNLOCKED", "via email OTP");
        notes.send(c, "SECURITY", "Card unlocked", "Your card ending " + c.cardNumber.substring(12) + " was unlocked using an email OTP.", true);
        ra.addFlashAttribute("msg", "Card unlocked. You can sign in now.");
        return "redirect:/login";
    }
}
