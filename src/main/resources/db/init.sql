-- ============================================================
-- AI 智能客服系统 - 数据库初始化
-- 执行: psql -U postgres -d smart_cs -f init.sql
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS users (
    id          SERIAL PRIMARY KEY,
    username    VARCHAR(64)  NOT NULL,
    phone       VARCHAR(20),
    email       VARCHAR(128),
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 订单表
CREATE TABLE IF NOT EXISTS orders (
    id              SERIAL PRIMARY KEY,
    order_no        VARCHAR(32)  NOT NULL UNIQUE,
    user_id         INTEGER      NOT NULL REFERENCES users(id),
    status          VARCHAR(20)  NOT NULL DEFAULT 'pending',
    amount          DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    product_name    VARCHAR(256),
    refund_status   VARCHAR(20),
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 售后工单表
CREATE TABLE IF NOT EXISTS tickets (
    id          SERIAL PRIMARY KEY,
    order_no    VARCHAR(32),
    user_id     INTEGER NOT NULL REFERENCES users(id),
    subject     VARCHAR(255) NOT NULL,
    description TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'pending',
    priority    VARCHAR(10) NOT NULL DEFAULT 'normal',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话历史表
CREATE TABLE IF NOT EXISTS chat_history (
    id          SERIAL PRIMARY KEY,
    session_id  VARCHAR(64)  NOT NULL,
    user_id     VARCHAR(64),
    role        VARCHAR(16)  NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX IF NOT EXISTS idx_orders_user_id ON orders(user_id);
CREATE INDEX IF NOT EXISTS idx_orders_order_no ON orders(order_no);
CREATE INDEX IF NOT EXISTS idx_tickets_user_id ON tickets(user_id);
CREATE INDEX IF NOT EXISTS idx_chat_session ON chat_history(session_id);
CREATE INDEX IF NOT EXISTS idx_chat_created ON chat_history(created_at);

-- ============================================================
-- 示例数据
-- ============================================================

INSERT INTO users (id, username, phone, email) VALUES
    (1, '张三', '13812345678', 'zhangsan@example.com'),
    (2, '李四', '13987654321', 'lisi@example.com'),
    (3, '王五', '13655558888', 'wangwu@example.com')
ON CONFLICT (id) DO NOTHING;

INSERT INTO orders (order_no, user_id, status, amount, product_name, refund_status, created_at) VALUES
    ('ORD20260601001', 1, 'delivered',   299.00, '蓝牙耳机 Pro',   NULL,      '2026-06-01 10:30:00'),
    ('ORD20260601002', 1, 'shipped',     159.00, '手机壳',         'processing', '2026-06-01 14:20:00'),
    ('ORD20260601003', 2, 'paid',         4999.00, '机械键盘',       NULL,      '2026-06-01 16:45:00'),
    ('ORD20260601004', 1, 'delivered',    89.00,  '数据线套装',     'completed', '2026-05-28 09:00:00'),
    ('ORD20260601005', 3, 'cancelled',    128.00, '桌面支架',       NULL,      '2026-05-25 11:00:00')
ON CONFLICT (order_no) DO NOTHING;
