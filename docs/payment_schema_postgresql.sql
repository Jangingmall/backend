-- 장인몰 결제/주문/배송/반품/이미지 업로드 운영 스키마 (PostgreSQL)
-- 운영 반영 전 백업 후 실행하고, 애플리케이션의 ddl-auto=validate로 검증한다.

begin;

create table if not exists cart (
    cart_id bigint generated always as identity primary key,
    member_id bigint references member(member_id),
    guest_cart_id varchar(50),
    updated_at timestamp with time zone not null default current_timestamp,
    constraint ck_cart_owner check ((member_id is null) <> (guest_cart_id is null)),
    constraint uk_cart_member unique (member_id),
    constraint uk_cart_guest unique (guest_cart_id)
);

create table if not exists cart_item (
    cart_item_id bigint generated always as identity primary key,
    cart_id bigint not null references cart(cart_id) on delete cascade,
    product_id bigint not null references product(product_id),
    quantity integer not null check (quantity > 0),
    selected_options jsonb,
    text_inputs jsonb,
    selected boolean not null default true
);

create index if not exists ix_cart_item_cart on cart_item(cart_id);

create table if not exists orders (
    order_id bigint generated always as identity primary key,
    order_number varchar(64) not null,
    member_id bigint not null references member(member_id),
    address_id bigint references address(address_id) on delete set null,
    recipient_name varchar(50) not null,
    recipient_phone varchar(20) not null,
    zip_code varchar(10) not null,
    address1 varchar(255) not null,
    address2 varchar(255),
    delivery_request varchar(100),
    payment_method varchar(20) not null default 'CARD',
    status varchar(20) not null default 'CREATED',
    total_amount bigint not null check (total_amount >= 0),
    shipping_amount bigint not null default 0 check (shipping_amount >= 0),
    client_request_key varchar(100),
    created_at timestamp with time zone not null default current_timestamp,
    canceled_at timestamp with time zone,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uk_orders_order_number unique (order_number),
    constraint uk_orders_member_client_request unique (member_id, client_request_key),
    constraint ck_orders_payment_method check (payment_method in ('CARD','TRANSFER','VIRTUAL_ACCOUNT','MOBILE','EASY_PAY')),
    constraint ck_orders_status check (status in ('CREATED','PAID','PAYMENT_FAILED','CANCELED','DELIVERED','RETURN_REQUESTED'))
);

-- 이미 orders 테이블이 있는 환경에서 이번 변경분을 보강한다.
alter table orders add column if not exists payment_method varchar(20) not null default 'CARD';
alter table orders add column if not exists shipping_amount bigint not null default 0;
create unique index if not exists uk_orders_member_client_request
    on orders(member_id, client_request_key) where client_request_key is not null;
create index if not exists ix_orders_expiration_scan on orders(status, updated_at);

create table if not exists order_item (
    order_item_id bigint generated always as identity primary key,
    order_id bigint not null references orders(order_id) on delete cascade,
    product_id bigint not null references product(product_id),
    product_name_snapshot varchar(200) not null,
    price_snapshot bigint not null check (price_snapshot >= 0),
    quantity integer not null check (quantity > 0),
    total_price bigint not null check (total_price >= 0),
    production_period_days_snapshot integer,
    selected_options_snapshot jsonb
);

create index if not exists ix_order_item_order on order_item(order_id);

create table if not exists payments (
    payment_id bigint generated always as identity primary key,
    order_id bigint not null references orders(order_id),
    amount bigint not null check (amount >= 0),
    method varchar(20) not null,
    payment_key varchar(255),
    status varchar(20) not null,
    failure_code varchar(100),
    failure_message varchar(255),
    version bigint not null default 0,
    approved_at timestamp with time zone,
    canceled_at timestamp with time zone,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uk_payments_order unique (order_id),
    constraint uk_payments_payment_key unique (payment_key),
    constraint ck_payments_method check (method in ('CARD','TRANSFER','VIRTUAL_ACCOUNT','MOBILE','EASY_PAY')),
    constraint ck_payments_status check (status in ('READY','DONE','FAILED','CANCELED'))
);

create table if not exists payment_method (
    payment_method_id bigint generated always as identity primary key,
    member_id bigint not null references member(member_id),
    type varchar(20) not null,
    card_company varchar(50) not null,
    card_number_masked varchar(30) not null,
    card_fingerprint varchar(64) not null,
    is_default boolean not null default false,
    created_at timestamp with time zone not null default current_timestamp,
    constraint uk_payment_method_member_fingerprint unique (member_id, card_fingerprint),
    constraint ck_payment_method_type check (type in ('CARD'))
);

create index if not exists ix_payment_method_member on payment_method(member_id);

create table if not exists refund_account (
    refund_account_id bigint generated always as identity primary key,
    member_id bigint not null references member(member_id),
    bank_name varchar(50) not null,
    account_number_masked varchar(30) not null,
    account_fingerprint varchar(64) not null,
    account_holder varchar(50) not null,
    created_at timestamp with time zone not null default current_timestamp
);

create index if not exists ix_refund_account_member on refund_account(member_id);

create table if not exists order_delivery (
    order_delivery_id bigint generated always as identity primary key,
    order_id bigint not null references orders(order_id),
    carrier_code varchar(20) not null,
    carrier_name varchar(100) not null,
    tracking_number varchar(100) not null,
    status varchar(20) not null,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uk_order_delivery_order unique (order_id),
    constraint ck_order_delivery_status check (status in ('SHIPPED','IN_TRANSIT','DELIVERED'))
);

create table if not exists image_upload (
    image_id varchar(30) primary key,
    member_id bigint not null references member(member_id),
    purpose varchar(20) not null,
    source_width integer not null check (source_width > 0),
    source_height integer not null check (source_height > 0),
    variants jsonb not null,
    consumed boolean not null default false,
    created_at timestamp with time zone not null default current_timestamp,
    expires_at timestamp with time zone not null,
    constraint ck_image_upload_purpose check (purpose in ('PRODUCT','ARTISAN','CONTENT','RETURN'))
);

create index if not exists ix_image_upload_expiration on image_upload(consumed, expires_at);

create table if not exists order_return (
    return_id bigint generated always as identity primary key,
    order_id bigint not null references orders(order_id),
    type varchar(20) not null,
    reason varchar(30) not null,
    reason_detail varchar(500),
    return_address_id bigint not null references address(address_id),
    order_item_ids jsonb not null default '[]'::jsonb,
    image_ids jsonb,
    status varchar(20) not null default 'REQUESTED',
    version bigint not null default 0,
    created_at timestamp with time zone not null default current_timestamp,
    updated_at timestamp with time zone not null default current_timestamp,
    constraint uk_order_return_order unique (order_id),
    constraint ck_order_return_type check (type in ('RETURN','EXCHANGE')),
    constraint ck_order_return_reason check (reason in ('CHANGE_OF_MIND','DEFECTIVE','WRONG_ITEM','WRONG_DELIVERY','OTHER')),
    constraint ck_order_return_status check (status in ('REQUESTED','APPROVED','REJECTED','COMPLETED'))
);

alter table order_return add column if not exists order_item_ids jsonb not null default '[]'::jsonb;

commit;
