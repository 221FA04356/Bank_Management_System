package com.bank.web;

import com.bank.repo.NotificationRepo;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class NotificationController extends Base {
    @Autowired NotificationRepo repo;

    /** Security alerts are grouped together in their own panel; everything else is under Activity. */
    @GetMapping("/notifications")
    public String page(HttpSession s, Model m) {
        Long id = uid(s);
        m.addAttribute("sec", repo.findTop50ByCustomerIdAndTypeOrderByIdDesc(id, "SECURITY"));
        m.addAttribute("other", repo.findTop50ByCustomerIdAndTypeNotOrderByIdDesc(id, "SECURITY"));
        repo.markAllSeen(id);
        m.addAttribute("unread", 0L);
        return "notifications";
    }
}
