package com.bank.config;

import com.bank.model.Customer;
import com.bank.repo.CustomerRepo;
import com.bank.repo.NotificationRepo;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/** Values every page needs: the logged-in customer, unread notification count, seconds left in the session. */
@ControllerAdvice
public class Advice {
    @Autowired CustomerRepo repo;
    @Autowired NotificationRepo notes;
    @Value("${app.session-minutes:20}") int minutes;

    private Long uid(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        return s == null ? null : (Long) s.getAttribute("uid");
    }

    @ModelAttribute("me")
    public Customer me(HttpServletRequest r) {
        Long id = uid(r);
        return id == null ? null : repo.findById(id).orElse(null);
    }

    @ModelAttribute("unread")
    public long unread(HttpServletRequest r) {
        Long id = uid(r);
        return id == null ? 0 : notes.countByCustomerIdAndSeenFalse(id);
    }

    @ModelAttribute("sessionLeft")
    public long sessionLeft(HttpServletRequest r) {
        HttpSession s = r.getSession(false);
        if (s == null || s.getAttribute("loginAt") == null) return 0;
        long left = minutes * 60L - (System.currentTimeMillis() - (Long) s.getAttribute("loginAt")) / 1000;
        return Math.max(0, left);
    }
}
