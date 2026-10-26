-- Indice de busqueda interno y almacen de coordenadas CIFRADAS (D15) por hallazgo.
-- hallazgo_id = UUID del hallazgo de Hernando (BINARY(16)). SIN FK todavia: ver doc 05, seccion 6.
-- NO hay columnas de latitud/longitud (ni en claro ni redondeadas): solo coord_cifrada.

CREATE TABLE ubicacion_hallazgo (
  hallazgo_id         BINARY(16)   NOT NULL,
  version             INT          NOT NULL,
  departamento_codigo VARCHAR(2)   NOT NULL,
  nombre_cientifico   VARCHAR(150) NOT NULL,
  fecha_recoleccion   DATE         NOT NULL,
  investigador        VARCHAR(100) NOT NULL,
  estado_conservacion VARCHAR(20)  NOT NULL,
  -- Cifrado AES-256-GCM. Payload = latitud y longitud como 2 doubles IEEE-754 big-endian (16 bytes);
  -- coord_cifrada = texto cifrado (16 B) || etiqueta GCM (16 B) = 32 B. AAD = UUID canonico (minusculas, 36 chars UTF-8).
  coord_cifrada       VARBINARY(64) NULL,
  coord_nonce         VARBINARY(12) NULL,
  clave_version       VARCHAR(16)   NULL,
  cifrado_en          DATETIME(3)   NULL,
  eliminado_en        DATETIME(3)   NULL,
  creado_en           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  actualizado_en      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_ubicacion_hallazgo PRIMARY KEY (hallazgo_id),
  CONSTRAINT fk_ubicacion_hallazgo_departamento FOREIGN KEY (departamento_codigo)
    REFERENCES ubicacion_departamento (codigo_dane) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_ubicacion_hallazgo_version CHECK (version >= 1),
  CONSTRAINT ck_ubicacion_hallazgo_estado CHECK (estado_conservacion IN ('EN_PELIGRO','NO_AMENAZADA','SIN_EVALUAR')),
  CONSTRAINT ck_ubicacion_hallazgo_nombre CHECK (CHAR_LENGTH(TRIM(nombre_cientifico)) > 0),
  CONSTRAINT ck_ubicacion_hallazgo_investigador CHECK (CHAR_LENGTH(TRIM(investigador)) > 0),
  -- Las 4 columnas de cifrado van juntas o ninguna (hallazgo sin coordenadas)
  CONSTRAINT ck_ubicacion_hallazgo_cifrado_completo CHECK (
    (coord_cifrada IS NULL AND coord_nonce IS NULL AND clave_version IS NULL AND cifrado_en IS NULL)
    OR
    (coord_cifrada IS NOT NULL AND coord_nonce IS NOT NULL AND clave_version IS NOT NULL AND cifrado_en IS NOT NULL)),
  CONSTRAINT ck_ubicacion_hallazgo_nonce_len CHECK (coord_nonce IS NULL OR LENGTH(coord_nonce) = 12),
  CONSTRAINT ck_ubicacion_hallazgo_cifrado_len CHECK (coord_cifrada IS NULL OR LENGTH(coord_cifrada) BETWEEN 17 AND 64),
  CONSTRAINT ck_ubicacion_hallazgo_clave CHECK (clave_version IS NULL OR clave_version REGEXP '^[A-Za-z0-9_-]{1,16}$'),

  -- Un (llave, nonce) repetido en GCM rompe la confidencialidad: el UNIQUE lo detecta si el aleatorio fallara.
  -- Su prefijo (clave_version) tambien sirve para contar/seleccionar registros por version en la rotacion.
  CONSTRAINT uq_ubicacion_hallazgo_clave_nonce UNIQUE (clave_version, coord_nonce)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Busqueda interna (HU-13): cada filtro combinable lleva la fecha como segunda columna,
-- porque el orden por defecto es fecha_recoleccion DESC y el rango de fechas es el filtro mas comun.
CREATE INDEX idx_ubicacion_hallazgo_fecha         ON ubicacion_hallazgo (fecha_recoleccion, hallazgo_id);
CREATE INDEX idx_ubicacion_hallazgo_especie_fecha ON ubicacion_hallazgo (nombre_cientifico, fecha_recoleccion);
CREATE INDEX idx_ubicacion_hallazgo_invest_fecha  ON ubicacion_hallazgo (investigador, fecha_recoleccion);
CREATE INDEX idx_ubicacion_hallazgo_depto_fecha   ON ubicacion_hallazgo (departamento_codigo, fecha_recoleccion);
