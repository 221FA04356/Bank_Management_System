# JavaBank - Bank Management System (Spring Boot + Thymeleaf + MySQL)

## Run locally (IntelliJ)
1. Install JDK 17+, MySQL 8 (remember the root password).
2. Open this folder in IntelliJ (File > Open > pom.xml > "Open as Project"). Wait for Maven to download dependencies.
3. Copy `.env.example` to `.env`, then fill in your local values such as `DB_PASS`, `MAIL_USER`, `MAIL_PASS`, and `ADMIN_PIN`.
   The database `bankSystem` and all tables are created automatically on first start.
   (`db/schema.sql` is there only if you want to create the tables by hand.)
4. Run `BankApplication`. Open http://localhost:8080
5. Admin login uses `ADMIN_CARD` and `ADMIN_PIN` from `.env`.
6. Without SMTP settings, OTP emails are printed in the IntelliJ console, so you can test everything immediately.

`.env` is listed in `.gitignore`, so it stays on your computer and should not be pushed to GitHub. Commit `.env.example`, not `.env`.

## Real OTP emails (SMTP)
Set these environment variables (IntelliJ: Run > Edit Configurations > Environment variables):

| Variable | Gmail example |
|---|---|
| MAIL_HOST | smtp.gmail.com |
| MAIL_PORT | 587 |
| MAIL_USER | yourname@gmail.com |
| MAIL_PASS | 16-character Google *App Password* (needs 2-step verification on) |
| MAIL_FROM | yourname@gmail.com |

## Deploy live (Docker)
The included `Dockerfile` works on Render, Railway, Fly.io, or any VPS.
1. Create a cloud MySQL database and note host, port, db name, user, password.
2. Push this folder to GitHub, create a "Web Service" from the repo (Docker).
3. Set environment variables:
   - `DB_URL` = `jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED&serverTimezone=UTC` (providers that give a `mysql://` URL: rewrite it in this JDBC form)
   - `DB_USER`, `DB_PASS`
   - `MAIL_HOST`, `MAIL_PORT`, `MAIL_USER`, `MAIL_PASS`, `MAIL_FROM`
   - `ADMIN_CARD`, `ADMIN_PIN` (strong values)
4. Deploy. The platform provides HTTPS and sets `PORT` automatically.
Note: some free hosting plans block outgoing SMTP ports (25/465/587). If OTP emails fail in the logs, use an SMTP provider that offers port 2525 (set MAIL_PORT=2525) or a host without that restriction.

## Push safely from IntelliJ IDEA
1. Open the Git tool window and check the changed files list.
2. Make sure `.env` is not listed. If it appears, do not commit; confirm `.gitignore` contains `.env`.
3. Do not commit `target/`; it is generated during build and ignored.
4. Commit source files, `.gitignore`, `.dockerignore`, `.env.example`, and `README.md`.
5. Push to GitHub from Git > Push.

## Feature map
Login/PIN (BCrypt) - 3-page signup - deposit - withdraw - fast cash - balance - mini statement - PIN change
Card lock after 3 wrong PINs (+ email-OTP unlock, admin unlock) - OTP for large/suspicious transactions
Fraud alerts - daily limit - transfers + beneficiaries - scheduled transfers - monthly interest
Savings goals - loans (EMI calculator, apply, admin approve/reject) - analytics charts
PDF statements - QR to receive money - English/Hindi - admin dashboard + audit log

## Email design
All emails are sent FROM one bank address (MAIL_USER / MAIL_FROM, shown as "JavaBank") TO one customer at a time.
For a transfer, the sender and the receiver each get their own separate email. The same applies to deposits,
withdrawals, scheduled transfers, money requests, loans, security alerts and the signup welcome email (PIN is never emailed).

## Real-time updates
The browser opens a WebSocket (/ws) after login. Loan approvals, incoming money, money-request approvals and security alerts
appear instantly as pop-ups and refresh the visible page section without a reload.

## Sessions and timeouts
Login lasts 20 minutes (countdown shown in the top bar, then automatic logout). Transfer review and add-beneficiary steps expire after 5 minutes.
