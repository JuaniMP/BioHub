-- Modelo de lectura publico. D14: NO existe ninguna columna de coordenadas (ni de ninguna especie).
-- El estado "ubicacionProtegida" no se guarda: con D14 es siempre true y se calcula en el servicio.
-- No se guarda investigador ni usuario del evento (HU-06.1, ultimo escenario).

CREATE TABLE ficha_publica (
  hallazgo_id         BINARY(16)   NOT NULL,
  version             INT          NOT NULL,
  nombre_cientifico   VARCHAR(150) NOT NULL,
  nombre_comun        VARCHAR(150) NULL,
  familia             VARCHAR(100) NOT NULL,
  tipo_especimen      VARCHAR(80)  NULL,
  fecha_recoleccion   DATE         NOT NULL,
  estado_conservacion VARCHAR(20)  NOT NULL,
  departamento_codigo VARCHAR(2)   NOT NULL,
  departamento_nombre VARCHAR(60)  NOT NULL,   -- desnormalizado (se muestra tal cual)
  region_nombre       VARCHAR(40)  NOT NULL,   -- desnormalizado
  descripcion         TEXT         NULL,       -- texto publico; el servicio limita a 5000 caracteres
  creado_en           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  actualizado_en      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_ficha_publica PRIMARY KEY (hallazgo_id),
  CONSTRAINT fk_ficha_publica_departamento FOREIGN KEY (departamento_codigo)
    REFERENCES departamento (codigo_dane) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_ficha_publica_version CHECK (version >= 1),
  CONSTRAINT ck_ficha_publica_estado CHECK (estado_conservacion IN ('EN_PELIGRO','NO_AMENAZADA','SIN_EVALUAR')),
  CONSTRAINT ck_ficha_publica_nombre CHECK (CHAR_LENGTH(TRIM(nombre_cientifico)) > 0),
  CONSTRAINT ck_ficha_publica_familia CHECK (CHAR_LENGTH(TRIM(familia)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Busqueda por q (HU-06.2): palabras de nombre cientifico y nombre comun
CREATE FULLTEXT INDEX ft_ficha_publica_nombres ON ficha_publica (nombre_cientifico, nombre_comun);
-- Listado sin filtros ordenado por nombre cientifico, y q de 1-2 letras con LIKE 'xx%'
CREATE INDEX idx_ficha_publica_nombre         ON ficha_publica (nombre_cientifico);
-- Filtros por familia y por departamento, ya ordenados por nombre
CREATE INDEX idx_ficha_publica_familia_nombre ON ficha_publica (familia, nombre_cientifico);
CREATE INDEX idx_ficha_publica_depto_nombre   ON ficha_publica (departamento_codigo, nombre_cientifico);

