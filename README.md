# JavaBank - Bank Management System

JavaBank is a full-stack bank management web application built with Spring Boot, Thymeleaf, MySQL, and Docker. It simulates core banking workflows such as customer signup, secure login, deposits, withdrawals, transfers, beneficiaries, OTP verification, statements, loans, savings goals, admin controls, audit logging, notifications, and real-time account updates.

The project is designed as an academic and portfolio-grade banking system that demonstrates secure configuration, layered backend architecture, server-rendered UI, database persistence, email integration, and deployable cloud-ready packaging.

## Project Scope

JavaBank focuses on the day-to-day workflows of a small digital banking platform:

- Customer account onboarding through a multi-step signup flow.
- PIN-based authentication with BCrypt password hashing.
- Account operations such as deposit, withdrawal, fast cash, balance check, and mini statements.
- Money transfers with beneficiary management and transfer review.
- OTP verification for large or suspicious transactions.
- Card/account lock and unlock behavior after repeated failed login attempts.
- Admin dashboard for user oversight, account unlocking, and loan decisions.
- Loan application, EMI calculation, and approval/rejection flow.
- Savings goals, scheduled transfers, monthly interest, and daily transaction limits.
- Email notifications and in-app real-time alerts.
- PDF statements and QR-based receive-money support.

This project is not intended to handle real banking data or production financial transactions. It is a learning and demonstration system.

## Key Features

- Secure login using card number and PIN.
- BCrypt PIN hashing.
- Three-page customer signup process.
- Deposit, withdrawal, fast cash, transfer, balance, and statement flows.
- Beneficiary add and confirm workflow.
- OTP verification for sensitive operations.
- Email support through SMTP.
- Console fallback for OTP messages when SMTP is not configured.
- Card locking after repeated failed PIN attempts.
- Admin unlock and email-OTP unlock support.
- Fraud alerts and configurable OTP threshold.
- Daily transaction limits.
- Scheduled transfers and monthly interest handling.
- Savings goals tracking.
- Loan EMI calculator, loan application, and admin approval.
- Analytics charts and account insights.
- PDF statement generation.
- QR code support for receiving money.
- English and Hindi message bundles.
- Admin dashboard with audit trail.
- WebSocket-based real-time notifications.
- Session timeout countdown and automatic logout.
- Docker-ready deployment setup.
- Environment-variable based configuration so secrets are not stored in source code.

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17+, Spring Boot 3.2.5 |
| Web MVC | Spring MVC |
| View layer | Thymeleaf, HTML, CSS, Bootstrap |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8 |
| Security utilities | Spring Security Crypto, BCrypt |
| Email | Spring Boot Mail, SMTP |
| Real-time updates | Spring WebSocket |
| PDF generation | OpenPDF |
| QR code support | ZXing |
| Build tool | Maven, Maven Wrapper |
| Deployment | Docker |

## Architecture

The application follows a layered Spring Boot architecture:

```text
Browser / Thymeleaf UI
        |
Spring MVC Controllers
        |
Service Layer
        |
Spring Data JPA Repositories
        |
MySQL Database
```

### Main Packages

- `com.bank.web` - MVC controllers for login, signup, account actions, admin screens, notifications, requests, and tools.
- `com.bank.service` - Business logic for banking operations, OTP, jobs, email, and notifications.
- `com.bank.repo` - Spring Data JPA repositories.
- `com.bank.model` - JPA entities such as customers, transactions, beneficiaries, loans, goals, requests, notifications, and audit records.
- `com.bank.config` - Web configuration, interceptors, WebSocket setup, global advice, and admin account seeding.

### Important Resources

- `src/main/resources/application.properties` - safe application configuration using environment variables.
- `src/main/resources/templates` - Thymeleaf pages.
- `src/main/resources/static/css/app.css` - application styling.
- `src/main/resources/messages.properties` - English messages.
- `src/main/resources/messages_hi.properties` - Hindi messages.
- `db/schema.sql` - optional database schema reference.
- `Dockerfile` - container build configuration.
- `.env.example` - safe environment variable template.

## Security and Configuration

Secrets are intentionally not stored in `application.properties`. The app reads sensitive values from environment variables or from a local `.env` file.

Local `.env` files are ignored by Git:

```text
.env
.env.*
```

Only `.env.example` should be committed.

Important environment variables:

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL for MySQL |
| `DB_USER` | Database username |
| `DB_PASS` | Database password |
| `MAIL_HOST` | SMTP host |
| `MAIL_PORT` | SMTP port |
| `MAIL_USER` | SMTP username |
| `MAIL_PASS` | SMTP password or app password |
| `MAIL_FROM` | Sender email address |
| `MAIL_FROM_NAME` | Sender display name |
| `ADMIN_CARD` | Admin login card number |
| `ADMIN_PIN` | Admin login PIN |
| `OTP_THRESHOLD` | Transaction amount threshold for OTP |
| `COOKIE_SECURE` | Enables secure cookies in HTTPS deployments |
| `THYMELEAF_CACHE` | Enables template caching in deployment |

The application refuses to start if `ADMIN_PIN` is blank, so deployments do not accidentally create an admin account with an empty PIN.

## Run Locally

### Prerequisites

- Java 17 or newer.
- MySQL 8.
- Git.

Maven does not need to be installed globally because the project includes Maven Wrapper.

### Steps

1. Clone or open the project folder.
2. Copy `.env.example` to `.env`.
3. Fill in your local database and admin values in `.env`.
4. Make sure MySQL is running.
5. Build the app:

```powershell
cmd /c mvnw.cmd -DskipTests package
```

6. Run the app:

```powershell
java -jar target\app.jar
```

7. Open:

```text
http://localhost:8080/login
```

The database and tables are created automatically through Hibernate when the app starts.

## IntelliJ IDEA Setup

1. Open IntelliJ IDEA.
2. Choose `File > Open`.
3. Select `pom.xml`.
4. Choose `Open as Project`.
5. Wait for Maven dependencies to load.
6. Create `.env` from `.env.example`.
7. Run `BankApplication`.

If you prefer IntelliJ Run Configurations, set the same environment variables from `.env` under:

```text
Run > Edit Configurations > Environment variables
```

## Real OTP Emails

To send real OTP emails, configure SMTP values in `.env` or in your deployment platform:

| Variable | Gmail example |
|---|---|
| `MAIL_HOST` | `smtp.gmail.com` |
| `MAIL_PORT` | `587` |
| `MAIL_USER` | `yourname@gmail.com` |
| `MAIL_PASS` | 16-character Google App Password |
| `MAIL_FROM` | `yourname@gmail.com` |

If `MAIL_HOST` is empty, OTP messages are printed in the console instead. This is useful for local testing.

## Deployment

The project includes a Dockerfile and can be deployed to platforms such as Render, Railway, Fly.io, or a VPS.

Deployment steps:

1. Create a cloud MySQL database.
2. Push this repository to GitHub.
3. Create a Docker-based web service from the GitHub repository.
4. Add environment variables in the hosting platform dashboard.
5. Deploy.

Example deployment variables:

```text
DB_URL=jdbc:mysql://HOST:PORT/DBNAME?sslMode=REQUIRED&serverTimezone=UTC
DB_USER=your_database_user
DB_PASS=your_database_password
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USER=your_email
MAIL_PASS=your_app_password
MAIL_FROM=your_email
ADMIN_CARD=1000000000000000
ADMIN_PIN=strong-admin-pin
COOKIE_SECURE=true
THYMELEAF_CACHE=true
```

Some free hosting providers block SMTP ports such as `25`, `465`, or `587`. If emails fail in deployment, use an email provider that supports an allowed port such as `2525`, or use a hosting provider that allows outbound SMTP.

## Findings and Achievements

- Implemented a practical banking workflow with customer, admin, transaction, notification, loan, and goal modules.
- Separated secrets from source code using environment variables and `.env` files.
- Added `.env.example` to document required deployment configuration safely.
- Added Docker support for cloud deployment.
- Added Maven Wrapper so the project can build without requiring a global Maven installation.
- Verified local startup with MySQL and confirmed the login page returns HTTP 200.
- Used BCrypt hashing for PIN storage instead of saving plain PINs.
- Added WebSocket notifications for real-time user feedback.
- Added PDF and QR code capabilities for richer banking interactions.

## Safe GitHub Push Checklist

Before pushing:

- Confirm `.env` is not shown in Git changes.
- Confirm `target/` is not committed.
- Confirm `*.log` files are not committed.
- Commit `.env.example`, not `.env`.
- Rotate any email app password or secret that was ever committed in the past.

Useful checks:

```powershell
git status
git check-ignore -v .env target/app.jar app.out.log
```

## Current Project Status

- Build: passes with Maven Wrapper.
- Local run: verified at `http://localhost:8080/login`.
- Database: MySQL connection verified locally.
- Deployment readiness: Dockerfile and environment-variable configuration are included.

