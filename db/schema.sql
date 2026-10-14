-- Optional: the app creates/updates these tables itself (spring.jpa.hibernate.ddl-auto=update).
-- Run this file in MySQL Workbench if you prefer to create the schema manually first.
CREATE DATABASE IF NOT EXISTS bankSystem CHARACTER SET utf8mb4;
USE bankSystem;

CREATE TABLE IF NOT EXISTS customers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255), father_name VARCHAR(255), dob VARCHAR(255), gender VARCHAR(255), email VARCHAR(255) UNIQUE,
  marital VARCHAR(255), address VARCHAR(255), city VARCHAR(255), state VARCHAR(255), pincode VARCHAR(255),
  religion VARCHAR(255), category VARCHAR(255), income VARCHAR(255), education VARCHAR(255), occupation VARCHAR(255),
  pan VARCHAR(255) UNIQUE, aadhar VARCHAR(255) UNIQUE, senior_citizen VARCHAR(255), existing_account VARCHAR(255),
  account_type VARCHAR(255), services VARCHAR(255),
  card_number VARCHAR(255) UNIQUE, account_number VARCHAR(255) UNIQUE,
  pin_hash VARCHAR(255),
  balance DECIMAL(38,2), daily_limit DECIMAL(38,2),
  failed_attempts INT NOT NULL DEFAULT 0, locked BIT(1) NOT NULL DEFAULT 0,
  role VARCHAR(255), created_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id BIGINT, type VARCHAR(255), amount DECIMAL(38,2), balance_after DECIMAL(38,2),
  note VARCHAR(255), created_at DATETIME(6),
  INDEX idx_txn_customer (customer_id, created_at)
);

CREATE TABLE IF NOT EXISTS beneficiaries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT, name VARCHAR(255), account_number VARCHAR(255),
  INDEX idx_ben_owner (owner_id)
);

CREATE TABLE IF NOT EXISTS goals (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT, name VARCHAR(255),
  target DECIMAL(38,2), saved DECIMAL(38,2), INDEX idx_goal_owner (owner_id)
);

CREATE TABLE IF NOT EXISTS standing_orders (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT, to_account VARCHAR(255), amount DECIMAL(38,2),
  day_of_month INT NOT NULL, last_run DATE, active BIT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS loans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, owner_id BIGINT, principal DECIMAL(38,2), rate DECIMAL(38,2), emi DECIMAL(38,2),
  months INT NOT NULL, status VARCHAR(255), created_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, actor VARCHAR(255), action VARCHAR(255), detail VARCHAR(500), created_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, customer_id BIGINT, type VARCHAR(255), title VARCHAR(255),
  message VARCHAR(1000), seen BIT(1) NOT NULL DEFAULT 0, created_at DATETIME(6),
  INDEX idx_notif_customer (customer_id, seen)
);

CREATE TABLE IF NOT EXISTS money_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY, requester_id BIGINT, payer_id BIGINT, amount DECIMAL(38,2),
  note VARCHAR(255), status VARCHAR(255), created_at DATETIME(6)
);
