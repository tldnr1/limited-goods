create table event (
    id             bigint generated always as identity primary key,
    sale_starts_at timestamptz not null
);

create table item (
    id              bigint generated always as identity primary key,
    event_id        bigint  not null references event (id),
    sale_quantity   integer not null check (sale_quantity > 0),
    per_buyer_limit integer not null check (per_buyer_limit > 0),
    price           bigint  not null check (price >= 0)
);

create table item_stock (
    item_id   bigint  primary key references item (id),
    remaining integer not null check (remaining >= 0)
);

create table purchase (
    id                  bigint generated always as identity primary key,
    event_id            bigint      not null references event (id),
    buyer_id            varchar(64) not null,
    status              varchar(20) not null check (status in ('HELD', 'CONFIRMED', 'FAILED')),
    failure_reason      varchar(20) check (failure_reason in ('EXPIRED', 'PAYMENT_UNKNOWN')),
    arrived_at          timestamptz not null,
    held_at             timestamptz not null,
    payment_deadline_at timestamptz not null,
    check ((status = 'FAILED') = (failure_reason is not null))
);

-- 진행 중 구매 하나 (Q3, ADR-0005)
create unique index purchase_one_held_per_buyer on purchase (event_id, buyer_id) where status = 'HELD';
-- 1인 한도 합계 조회
create index purchase_buyer on purchase (event_id, buyer_id);
-- 결제 시작 기한 만료 대상
create index purchase_expiry on purchase (payment_deadline_at) where status = 'HELD';

create table purchase_line (
    id          bigint generated always as identity primary key,
    purchase_id bigint  not null references purchase (id),
    item_id     bigint  not null references item (id),
    quantity    integer not null check (quantity > 0),
    unique (purchase_id, item_id)
);

create table payment (
    id                      bigint generated always as identity primary key,
    purchase_id             bigint       not null references purchase (id),
    status                  varchar(20)  not null check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCEL_PENDING', 'CANCELED')),
    client_request_key      varchar(300) not null,
    pg_order_id             varchar(64)  not null unique,
    confirm_idempotency_key varchar(300) not null,
    cancel_idempotency_key  varchar(300),
    payment_key             varchar(200),
    amount                  bigint       not null check (amount >= 0),
    arrived_at              timestamptz  not null,
    confirm_deadline_at     timestamptz  not null,
    next_check_at           timestamptz,
    check_count             integer      not null default 0,
    unresolved_at           timestamptz,
    unique (purchase_id, client_request_key)
);

-- 결제는 한 번에 하나 (P2)
create unique index payment_one_pending_per_purchase on payment (purchase_id) where status = 'PENDING';
-- 재확인·취소 대상 (ADR-0002, ADR-0007)
create index payment_recheck on payment (next_check_at) where status in ('PENDING', 'CANCEL_PENDING');
