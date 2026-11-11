-- Consumidores externos (universidades y ONG) y sus API Keys: HU-08, HU-12, RF14, RNF11, ASR8.
-- Los contadores del rate limit NO van aqui: viven en Redis y duran un minuto.
-- Compatible con Aiven: PK en todas las tablas, sin triggers ni CREATE TABLE ... SELECT.

CREATE TABLE consumidor_externo (
  id                BINARY(16)   NOT NULL,
  nombre            VARCHAR(150) NOT NULL,
  contacto_email    VARCHAR(254) NOT NULL,
  -- NULL = usa el limite por defecto (biohub.gateway.limites.por-consumidor, 60 por minuto).
  limite_por_minuto INT          NULL,
  creado_por        VARCHAR(64)  NOT NULL,   -- sub del administrador (SEC-17)
  creado_en         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  actualizado_en    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_consumidor_externo PRIMARY KEY (id),
  CONSTRAINT uq_consumidor_externo_nombre UNIQUE (nombre),
  CONSTRAINT ck_consumidor_externo_limite CHECK (limite_por_minuto IS NULL OR limite_por_minuto BETWEEN 1 AND 100000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE api_key (
  id            BINARY(16)  NOT NULL,
  consumidor_id BINARY(16)  NOT NULL,
  -- Parte visible de la key (bh_pub_xxxxxxxx): sirve para buscarla y mostrarla sin revelar el secreto.
  prefijo       VARCHAR(20) NOT NULL,
  -- SHA-256 de la key completa. La key en claro nunca se guarda: se muestra una sola vez al emitirla.
  hash_sha256   BINARY(32)  NOT NULL,
  estado        VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
  expira_en     DATETIME(3) NULL,
  ultimo_uso_en DATETIME(3) NULL,
  creada_por    VARCHAR(64) NOT NULL,
  creada_en     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  revocada_por  VARCHAR(64) NULL,
  revocada_en   DATETIME(3) NULL,

  CONSTRAINT pk_api_key PRIMARY KEY (id),
  CONSTRAINT uq_api_key_prefijo UNIQUE (prefijo),
  CONSTRAINT fk_api_key_consumidor FOREIGN KEY (consumidor_id)
    REFERENCES consumidor_externo (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_api_key_estado CHECK (estado IN ('ACTIVA','REVOCADA')),
  CONSTRAINT ck_api_key_revocacion CHECK (
    (estado = 'ACTIVA' AND revocada_en IS NULL AND revocada_por IS NULL)
    OR (estado = 'REVOCADA' AND revocada_en IS NOT NULL AND revocada_por IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_api_key_consumidor ON api_key (consumidor_id);
