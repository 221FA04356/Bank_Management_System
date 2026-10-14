package com.bank.config;

import jakarta.servlet.http.*;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthInterceptor implements HandlerInterceptor {
    private final long maxMs;

    public AuthInterceptor(int minutes) { this.maxMs = minutes * 60_000L; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("uid") == null) {
            if ("GET".equals(req.getMethod())) {
                String q = req.getQueryString();
                req.getSession().setAttribute("next", req.getRequestURI() + (q == null ? "" : "?" + q));
            }
            res.sendRedirect("/login");
            return false;
        }
        // hard limit: the session ends N minutes after login, however active the user is
        Long at = (Long) s.getAttribute("loginAt");
        if (at == null || System.currentTimeMillis() - at > maxMs) {
            s.invalidate();
            res.sendRedirect("/login?expired");
            return false;
        }
        if (req.getRequestURI().startsWith("/admin") && !"ADMIN".equals(s.getAttribute("role"))) {
            res.sendError(403);
            return false;
        }
        return true;
    }
}
