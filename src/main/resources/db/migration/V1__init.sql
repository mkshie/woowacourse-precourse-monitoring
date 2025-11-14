
create table if not exists items (

    id bigserial primary key,
    name varchar(120) not null,
    price integer not null check (price >= 0),
    stock integer not null check (stock >= 0),
    tags text,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMP
    );

create table if not exists orders (

    id bigserial primary key,
    item_id bigint not null references items(id),
    quantity integer not null check (quantity > 0),
    total_price integer not null check (total_price >= 0),
    created_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMP
    );

-- 예제 데이터
INSERT INTO items(name, price, stock, tags) VALUES
                                                ('Java 초급', 12000, 7, 'java,beginner'),
                                                ('Spring 입문', 19000, 5, 'spring,java'),
                                                ('ElasticSearch 맛보기', 15000, 3, 'search,es')
ON CONFLICT DO NOTHING;
