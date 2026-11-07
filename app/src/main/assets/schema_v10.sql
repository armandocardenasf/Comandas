CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE,
            password TEXT,
            role TEXT CHECK(role IN ('admin','cajero','super_cajero')),
            permissions TEXT DEFAULT '',
            status TEXT DEFAULT 'Activo',
            fecha_alta TEXT DEFAULT CURRENT_TIMESTAMP,            
            ultimo_acceso TEXT
          );
CREATE TABLE Folios (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            folio_comanda INTEGER DEFAULT 0,
            folio_cierre INTEGER DEFAULT 0,
            folio_venta INTEGER DEFAULT 0
          );
CREATE TABLE Empresa (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT NOT NULL,
            rfc TEXT,
            direccion TEXT,
            telefono TEXT,
            email TEXT,
            tipo_cambio REAL DEFAULT 1.0
          );
CREATE TABLE productos (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            codigo TEXT UNIQUE NOT NULL,
            nombre TEXT,
            descripcion TEXT,
            precio REAL CHECK(
              precio > 0 AND 
              CAST(precio * 100 AS INTEGER) = precio * 100
            ),
            category_id INTEGER,
            foto TEXT,
            stock REAL CHECK(stock >= 0) DEFAULT 0,
            status TEXT CHECK(status IN ('Activo', 'No Activo')) DEFAULT 'Activo',
            fecha_alta TEXT DEFAULT CURRENT_TIMESTAMP,
            FOREIGN KEY (category_id) REFERENCES categories(id) ON UPDATE CASCADE
          );
CREATE TABLE producto_variantes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            producto_id INTEGER NOT NULL,
            nombre TEXT NOT NULL,
            factor_descarga REAL DEFAULT 1.0 CHECK(factor_descarga > 0),
            precio REAL NOT NULL CHECK(
              precio > 0 AND
              CAST(precio * 100 AS INTEGER) = precio * 100
              ),
              FOREIGN KEY (producto_id) REFERENCES productos(id) ON DELETE CASCADE
            );
CREATE TABLE empleados (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT,
            categoria TEXT CHECK(categoria IN ('Administrador','Atencion')),
            status TEXT CHECK(status IN ('Activo', 'No Activo')) DEFAULT 'Activo',            
            fecha_alta TEXT DEFAULT CURRENT_TIMESTAMP
          );
CREATE TABLE clientes (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT,
            nivel INTEGER,
            telefono TEXT,
            email TEXT
          );
CREATE TABLE categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT
          );
CREATE TABLE tipo_pago (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            nombre TEXT UNIQUE,
            factor REAL NOT NULL DEFAULT 1.0
          );
CREATE TABLE mesas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            numero INTEGER UNIQUE,
            nombre TEXT,
            disponibilidad TEXT CHECK(disponibilidad IN('Disponible','Ocupada')) DEFAULT 'Disponible'
          );
CREATE TABLE menu (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_id INTEGER NOT NULL,
            measurement_type TEXT,
            FOREIGN KEY (product_id) REFERENCES productos(id) ON DELETE CASCADE ON UPDATE CASCADE
          );
CREATE TABLE orders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            folio_comanda INTEGER UNIQUE NOT NULL,
            fecha TEXT NOT NULL,
            hora TEXT NOT NULL,
            mesa INTEGER NOT NULL,
            personas INTEGER NOT NULL,
            id_cliente INTEGER,
            status TEXT CHECK(status IN ('abierto', 'pagado')) NOT NULL DEFAULT 'abierto',
            monto_por_pagar REAL DEFAULT 0,
            monto_pagado REAL DEFAULT 0,
            discount_type TEXT DEFAULT '%',
            general_discount REAL DEFAULT 0,
            observaciones TEXT,
            FOREIGN KEY (mesa) REFERENCES mesas(id),
            FOREIGN KEY (id_cliente) REFERENCES clientes(id)
          );
CREATE TABLE order_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            order_id INTEGER,
            product_id INTEGER,
            product_code TEXT,
            product_name TEXT,
            product_descripcion TEXT,
            quantity INTEGER,
            price REAL,
            total REAL,
            discount_percent REAL DEFAULT 0,
            discount_amount REAL DEFAULT 0,
            product_variant_id INTEGER,
            FOREIGN KEY (order_id) REFERENCES orders(id),
            FOREIGN KEY (product_id) REFERENCES productos(id),
            FOREIGN KEY (product_variant_id) REFERENCES producto_variantes(id)
          );
CREATE TABLE sales (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            folio_venta INTEGER UNIQUE NOT NULL,
            folio_comanda INTEGER UNIQUE NOT NULL,
            fecha TEXT NOT NULL,
            hora TEXT NOT NULL,
            mesa INTEGER NOT NULL,
            personas INTEGER NOT NULL,
            id_cliente INTEGER,
            status TEXT CHECK(status IN ('abierto', 'pagado')) NOT NULL DEFAULT 'abierto',
            monto_por_pagar REAL DEFAULT 0,
            monto_pagado REAL DEFAULT 0,
            propina REAL DEFAULT 0,
            discount_type TEXT DEFAULT '%',
            general_discount REAL DEFAULT 0,
            observaciones TEXT,
            FOREIGN KEY (mesa) REFERENCES mesas(id),
            FOREIGN KEY (id_cliente) REFERENCES clientes(id)
          );
CREATE TABLE sale_payments (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sale_id INTEGER NOT NULL,
            tipo_pago_id INTEGER NOT NULL,       -- FK a tipo_pago
            fecha TEXT NOT NULL,                 -- e.g. '2025-06-24'
            hora TEXT NOT NULL,                  -- e.g. '18:30:00'
            monto_pagado REAL NOT NULL,          -- Monto sin propina
            propina REAL DEFAULT 0,              -- Monto de la propina
            moneda TEXT NOT NULL DEFAULT 'MXN',  -- 'MXN' o 'USD'
            persona TEXT DEFAULT NULL,           -- identificador de quién paga
            total_pago REAL GENERATED ALWAYS AS (monto_pagado + propina) STORED,
            FOREIGN KEY (sale_id)    REFERENCES sales(id),
            FOREIGN KEY (tipo_pago_id) REFERENCES tipo_pago(id)
          );
CREATE TABLE sale_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sale_id INTEGER,
            product_id INTEGER,
            product_variant_id INTENGER,
            product_code TEXT,
            product_name TEXT,
            product_descripcion TEXT,
            quantity INTEGER,
            price REAL,
            total REAL,
            discount_percent REAL DEFAULT 0,
            discount_amount REAL DEFAULT 0,
            FOREIGN KEY (sale_id) REFERENCES sales(id),
            FOREIGN KEY (product_id) REFERENCES productos(id),
            FOREIGN KEY (product_variant_id) REFERENCES producto_variantes(id)
          );
CREATE TABLE sale_item_assignment (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sale_payment_id INTEGER NOT NULL,
            order_item_id INTEGER NOT NULL,
            FOREIGN KEY (sale_payment_id) REFERENCES sale_payments(id),
            FOREIGN KEY (order_item_id) REFERENCES order_items(id)
          );
CREATE TABLE cierres (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            tipo TEXT CHECK(tipo IN ('parcial', 'total')) NOT NULL,
            fecha TEXT NOT NULL,
            hora TEXT NOT NULL,
            monto_total REAL NOT NULL,
            observaciones TEXT
          );