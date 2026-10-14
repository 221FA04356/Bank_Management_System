package com.bank.web;

import com.bank.model.*;
import com.bank.repo.*;
import com.bank.service.BankService;
import com.bank.service.NotificationService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.List;

@Controller
public class ToolsController extends Base {
    @Autowired BankService bank;
    @Autowired NotificationService notes;
    @Autowired TxnRepo txns;
    @Autowired GoalRepo goalRepo;
    @Autowired StandingRepo standing;
    @Autowired LoanRepo loanRepo;
    @Autowired BCryptPasswordEncoder enc;

    // ================= Savings goals =================
    @GetMapping("/goals")
    public String goals(HttpSession s, Model m) { m.addAttribute("goals", goalRepo.findByOwnerId(uid(s))); return "goals"; }

    @PostMapping("/goals")
    public String addGoal(@RequestParam String name, @RequestParam String target, HttpSession s, RedirectAttributes ra) {
        BigDecimal t = amount(target);
        if (name.isBlank() || t == null) { ra.addFlashAttribute("err", "Enter a goal name and a valid target."); return "redirect:/goals"; }
        Goal g = new Goal();
        g.ownerId = uid(s); g.name = name.trim(); g.target = t;
        goalRepo.save(g);
        ra.addFlashAttribute("msg", "Goal created.");
        return "redirect:/goals";
    }

    @PostMapping("/goals/{id}/move")
    public String moveGoal(@PathVariable Long id, @RequestParam String amount, @RequestParam String dir, HttpSession s, RedirectAttributes ra) {
        String err = bank.goalMove(uid(s), id, amount(amount), "in".equals(dir));
        if (err != null) ra.addFlashAttribute("err", err); else ra.addFlashAttribute("msg", "Done.");
        return "redirect:/goals";
    }

    @PostMapping("/goals/{id}/delete")
    public String delGoal(@PathVariable Long id, HttpSession s, RedirectAttributes ra) {
        Goal g = goalRepo.findById(id).filter(x -> x.ownerId.equals(uid(s))).orElse(null);
        if (g != null && g.saved.signum() == 0) goalRepo.delete(g);
        else ra.addFlashAttribute("err", "Withdraw the saved money back to your account before deleting this goal.");
        return "redirect:/goals";
    }

    // ================= Scheduled / recurring transfers =================
    @GetMapping("/scheduled")
    public String scheduled(HttpSession s, Model m) { m.addAttribute("items", standing.findByOwnerId(uid(s))); return "scheduled"; }

    @PostMapping("/scheduled")
    public String addScheduled(@RequestParam String to, @RequestParam String amount, @RequestParam int day, HttpSession s, RedirectAttributes ra) {
        Customer t = customers.findByAccountNumber(to.trim()).orElse(null);
        BigDecimal a = amount(amount);
        if (t == null || "ADMIN".equals(t.role) || t.id.equals(uid(s)) || a == null || day < 1 || day > 28) {
            ra.addFlashAttribute("err", "Enter a valid account number of another customer, an amount, and a day between 1 and 28.");
        } else {
            StandingOrder o = new StandingOrder();
            o.ownerId = uid(s); o.toAccount = t.accountNumber; o.amount = a; o.dayOfMonth = day;
            standing.save(o);
            Customer owner = me(s);
            String last4 = t.accountNumber.substring(t.accountNumber.length() - 4);
            notes.send(owner, "TRANSACTION", "Recurring transfer scheduled",
                    "You scheduled Rs. " + a + " to " + t.name + " (a/c xxxxxx" + last4 + ") on day " + day + " of every month.", true);
            notes.send(t, "TRANSACTION", "Incoming recurring transfer scheduled",
                    owner.name + " has set up a recurring transfer of Rs. " + a + " to your account on day " + day + " of every month.", true);
            ra.addFlashAttribute("msg", "Recurring transfer scheduled for day " + day + " of every month.");
        }
        return "redirect:/scheduled";
    }

    @PostMapping("/scheduled/{id}/delete")
    public String delScheduled(@PathVariable Long id, HttpSession s) {
        standing.findById(id).filter(o -> o.ownerId.equals(uid(s))).ifPresent(standing::delete);
        return "redirect:/scheduled";
    }

    // ================= Loans =================
    @GetMapping("/loans")
    public String loans(HttpSession s, Model m) {
        m.addAttribute("loans", loanRepo.findByOwnerIdOrderByIdDesc(uid(s)));
        m.addAttribute("rate", bank.getLoanRate());
        return "loans";
    }

    @PostMapping("/loans")
    public String applyLoan(@RequestParam String amount, @RequestParam int months, HttpSession s, RedirectAttributes ra) {
        BigDecimal a = amount(amount);
        if (a == null || a.compareTo(new BigDecimal("1000000")) > 0 || months < 3 || months > 60) {
            ra.addFlashAttribute("err", "Loan amount must be up to Rs. 10,00,000 and tenure 3 to 60 months.");
        } else {
            bank.applyLoan(uid(s), a, months);
            ra.addFlashAttribute("msg", "Loan application submitted. The bank will review it.");
        }
        return "redirect:/loans";
    }

    // ================= Analytics =================
    @GetMapping("/analytics")
    public String analytics(HttpSession s, Model m) {
        LocalDate start = LocalDate.now().withDayOfMonth(1).minusMonths(5);
        List<Txn> list = txns.findByCustomerIdAndCreatedAtAfter(uid(s), start.atStartOfDay());
        List<String> labels = new ArrayList<>();
        List<Double> credits = new ArrayList<>(), debits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            YearMonth ym = YearMonth.from(start.plusMonths(i));
            double c = 0, d = 0;
            for (Txn t : list) {
                if (!YearMonth.from(t.createdAt).equals(ym)) continue;
                if (BankService.CREDIT_TYPES.contains(t.type)) c += t.amount.doubleValue(); else d += t.amount.doubleValue();
            }
            labels.add(ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + ym.getYear());
            credits.add(c); debits.add(d);
        }
        Map<String, Double> byType = new TreeMap<>();
        for (Txn t : list) byType.merge(t.type, t.amount.doubleValue(), Double::sum);
        m.addAttribute("labels", labels);
        m.addAttribute("credits", credits);
        m.addAttribute("debits", debits);
        m.addAttribute("typeLabels", new ArrayList<>(byType.keySet()));
        m.addAttribute("typeValues", new ArrayList<>(byType.values()));
        return "analytics";
    }

    // ================= Statements =================
    private static LocalDate date(String s, LocalDate def) {
        try { return LocalDate.parse(s); } catch (Exception e) { return def; }
    }

    private List<Txn> range(Long id, LocalDate f, LocalDate t) {
        return txns.findByCustomerIdAndCreatedAtBetweenOrderByIdDesc(id, f.atStartOfDay(), t.plusDays(1).atStartOfDay());
    }

    @GetMapping("/statement")
    public String statement(@RequestParam(required = false) String from, @RequestParam(required = false) String to, HttpSession s, Model m) {
        LocalDate t = date(to, LocalDate.now()), f = date(from, t.minusDays(30));
        m.addAttribute("from", f.toString());
        m.addAttribute("to", t.toString());
        m.addAttribute("list", range(uid(s), f, t));
        return "statement";
    }

    @GetMapping("/statement/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam(required = false) String from, @RequestParam(required = false) String to, HttpSession s) {
        Customer c = me(s);
        LocalDate t = date(to, LocalDate.now()), f = date(from, t.minusDays(30));
        List<Txn> list = range(c.id, f, t);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Document d = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(d, out);
            d.open();
            d.add(new Paragraph("JavaBank - Account Statement", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            d.add(new Paragraph(c.name + "\nAccount: " + c.accountNumber + "   Card: xxxx-xxxx-xxxx-" + c.cardNumber.substring(12)
                    + "\nPeriod: " + f + " to " + t + "\nCurrent balance: Rs. " + c.balance + "\n\n"));
            PdfPTable tb = new PdfPTable(5);
            tb.setWidthPercentage(100);
            tb.setWidths(new float[]{3, 2.2f, 4, 2, 2});
            for (String h : new String[]{"Date", "Type", "Details", "Amount (Rs.)", "Balance (Rs.)"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
                cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                tb.addCell(cell);
            }
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);
            for (Txn x : list) {
                tb.addCell(new Phrase(x.createdAt.format(fmt), small));
                tb.addCell(new Phrase(x.type, small));
                tb.addCell(new Phrase(x.note == null ? "" : x.note, small));
                tb.addCell(new Phrase(x.amount.toPlainString(), small));
                tb.addCell(new Phrase(x.balanceAfter.toPlainString(), small));
            }
            d.add(tb);
            d.close();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=statement-" + f + "-to-" + t + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF).body(out.toByteArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ================= Settings: daily limit, PIN change, QR =================
    @GetMapping("/settings") public String settings() { return "settings"; }

    @PostMapping("/settings/limit")
    public String limit(@RequestParam String limit, HttpSession s, RedirectAttributes ra) {
        BigDecimal l = amount(limit);
        if (l == null || l.compareTo(new BigDecimal("100")) < 0 || l.compareTo(new BigDecimal("500000")) > 0) {
            ra.addFlashAttribute("err", "Daily limit must be between Rs. 100 and Rs. 5,00,000.");
        } else {
            Customer c = me(s);
            c.dailyLimit = l;
            customers.save(c);
            notes.send(c, "SECURITY", "Daily limit changed", "Your daily withdrawal/transfer limit was changed to Rs. " + l + ".", true);
            ra.addFlashAttribute("msg", "Daily limit updated to Rs. " + l);
        }
        return "redirect:/settings";
    }

    @PostMapping("/pin")
    public String pin(@RequestParam String oldPin, @RequestParam String newPin, @RequestParam String newPin2, HttpSession s, RedirectAttributes ra) {
        Customer c = me(s);
        if (!enc.matches(oldPin, c.pinHash)) ra.addFlashAttribute("err", "Current PIN is incorrect.");
        else if (!pin4(newPin) || !newPin.equals(newPin2)) ra.addFlashAttribute("err", "New PIN must be 4 digits and both entries must match.");
        else {
            c.pinHash = enc.encode(newPin);
            customers.save(c);
            bank.audit(c.cardNumber, "PIN_CHANGED", "ok");
            notes.send(c, "SECURITY", "PIN changed", "Your card PIN was changed. If this was not you, contact the bank immediately.", true);
            ra.addFlashAttribute("msg", "PIN changed successfully.");
        }
        return "redirect:/settings";
    }

    /** QR that opens the transfer page with this account pre-filled. */
    @GetMapping(value = "/qr.png", produces = MediaType.IMAGE_PNG_VALUE)
    @ResponseBody
    public byte[] qr(HttpServletRequest req, HttpSession s) throws Exception {
        Customer c = me(s);
        String url = ServletUriComponentsBuilder.fromContextPath(req).path("/transfer").queryParam("to", c.accountNumber).toUriString();
        BitMatrix bm = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 260, 260);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bm, "PNG", o);
        return o.toByteArray();
    }
}
