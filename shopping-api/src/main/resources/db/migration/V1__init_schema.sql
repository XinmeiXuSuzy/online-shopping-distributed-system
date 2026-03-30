-- ============================================================
-- V1: Initial schema — Online Shopping Distributed System
-- MySQL 8.0 | InnoDB | UTF8MB4
-- ============================================================

-- ============================================================
-- Table: users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT          NOT NULL,
    username      VARCHAR(64)     NOT NULL,
    email         VARCHAR(128)    NOT NULL,
    password_hash VARCHAR(256)    NOT NULL,
    phone         VARCHAR(20)             ,
    status        TINYINT         NOT NULL DEFAULT 1
                                  COMMENT '1=active, 0=disabled',
    created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                           ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at    DATETIME(3)              DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email    (email),
    UNIQUE KEY uk_users_username (username),
    KEY         idx_users_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- Table: products
-- ============================================================
CREATE TABLE IF NOT EXISTS products (
    id           BIGINT           NOT NULL,
    name         VARCHAR(256)     NOT NULL,
    description  TEXT                     ,
    price        DECIMAL(12, 2)   NOT NULL,
    category     VARCHAR(64)              ,
    image_url    VARCHAR(512)             ,
    status       TINYINT          NOT NULL DEFAULT 1
                                  COMMENT '1=on_sale, 0=off_shelf',
    created_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)      NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                           ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at   DATETIME(3)               DEFAULT NULL,

    PRIMARY KEY (id),
    KEY idx_products_category (category),
    KEY idx_products_status   (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- Table: inventory
-- Separated from products to allow high-frequency stock updates
-- without row-level contention on the wide products row.
-- ============================================================
CREATE TABLE IF NOT EXISTS inventory (
    id               BIGINT     NOT NULL,
    product_id       BIGINT     NOT NULL,
    total_stock      INT        NOT NULL DEFAULT 0
                                COMMENT 'physical warehouse quantity',
    available_stock  INT        NOT NULL DEFAULT 0
                                COMMENT 'what users can buy (total minus locked)',
    locked_quantity  INT        NOT NULL DEFAULT 0
                                COMMENT 'units held by active TCC TRYING transactions',
    version          INT        NOT NULL DEFAULT 0
                                COMMENT 'optimistic lock version',
    updated_at       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                          ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_product (product_id),
    KEY idx_inventory_available (available_stock)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- Table: orders
-- ============================================================
CREATE TABLE IF NOT EXISTS orders (
    id             BIGINT          NOT NULL,
    user_id        BIGINT          NOT NULL,
    status         VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                                   COMMENT 'PENDING|CONFIRMED|CANCELLED|FAILED',
    total_amount   DECIMAL(14, 2)  NOT NULL,
    currency       CHAR(3)         NOT NULL DEFAULT 'USD',
    note           VARCHAR(512)             ,
    sqs_message_id VARCHAR(128)             COMMENT 'SQS MessageId for idempotency tracking',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                            ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_orders_user_id    (user_id),
    KEY idx_orders_status     (status),
    KEY idx_orders_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- Table: order_items
-- ============================================================
CREATE TABLE IF NOT EXISTS order_items (
    id          BIGINT         NOT NULL,
    order_id    BIGINT         NOT NULL,
    product_id  BIGINT         NOT NULL,
    quantity    INT            NOT NULL,
    unit_price  DECIMAL(12, 2) NOT NULL   COMMENT 'price snapshot at order time',
    subtotal    DECIMAL(14, 2) NOT NULL,
    created_at  DATETIME(3)    NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    KEY idx_order_items_order_id   (order_id),
    KEY idx_order_items_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


-- ============================================================
-- Table: tcc_transactions
-- TCC coordinator journal. One row per Try attempt.
-- Confirm/Cancel phases reference this row for idempotency.
-- ============================================================
CREATE TABLE IF NOT EXISTS tcc_transactions (
    id              BIGINT       NOT NULL,
    order_id        BIGINT       NOT NULL,
    product_id      BIGINT       NOT NULL,
    quantity        INT          NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'TRYING'
                                 COMMENT 'TRYING|CONFIRMED|CANCELLED',
    try_expire_at   DATETIME(3)  NOT NULL
                                 COMMENT 'auto-cancel if still TRYING past this timestamp',
    confirmed_at    DATETIME(3)            DEFAULT NULL,
    cancelled_at    DATETIME(3)            DEFAULT NULL,
    cancel_reason   VARCHAR(256)           DEFAULT NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                          ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (id),
    UNIQUE KEY uk_tcc_order_product (order_id, product_id),
    KEY idx_tcc_status_expire  (status, try_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
