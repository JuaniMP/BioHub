-- Modulo REGISTRO (Hernando): hallazgos, fichas taxonomicas, historial y auditoria.
-- HU-01 (registro), HU-02 (atributos flexibles), HU-03 (reclasificacion), HU-05 (auditoria inmutable).
--
-- Supuestos acordados con ubicacion (doc 05 seccion 6):
--   - id del hallazgo = UUID generado por la aplicacion, BINARY(16), PK, InnoDB.
--   - version crece estrictamente por hallazgo; es la misma que viaja a UbicacionService y al evento.
--   - estado_conservacion usa los 3 valores del contrato.
-- NUNCA se guardan latitud/longitud aqui: las coordenadas solo viven cifradas en ubicacion_hallazgo (D14, RNF9).

-- ---------------------------------------------------------------------------
-- Plantillas de ficha taxonomica (HU-02, ASR5)
-- Los atributos personalizados viven en una columna JSON: agregar un campo nuevo
-- no altera el esquema ni los hallazgos ya guardados, que conservan su version de plantilla.
-- ---------------------------------------------------------------------------
CREATE TABLE ficha_plantilla (
  id                BINARY(16)   NOT NULL,
  nombre_cientifico VARCHAR(150) NOT NULL,
  version           INT          NOT NULL,
  -- [{"clave":"temperatura_agua","etiqueta":"Temperatura del agua","tipo":"NUMERO",
  --   "obligatorio":false,"habilitado":true,"unidad":"C"}, ...]
  atributos         JSON         NOT NULL,
  creado_por        VARCHAR(100) NOT NULL,
  creado_en         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_ficha_plantilla PRIMARY KEY (id),
  CONSTRAINT uq_ficha_plantilla_especie_version UNIQUE (nombre_cientifico, version),
  CONSTRAINT ck_ficha_plantilla_version CHECK (version >= 1),
  CONSTRAINT ck_ficha_plantilla_nombre CHECK (CHAR_LENGTH(TRIM(nombre_cientifico)) > 0),
  CONSTRAINT ck_ficha_plantilla_atributos CHECK (JSON_TYPE(atributos) = 'ARRAY')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ---------------------------------------------------------------------------
-- Hallazgo: estado ACTUAL. El historial completo vive en hallazgo_version.
-- ---------------------------------------------------------------------------
CREATE TABLE hallazgo (
  id                  BINARY(16)   NOT NULL,
  version             INT          NOT NULL,
  nombre_cientifico   VARCHAR(150) NOT NULL,
  nombre_comun        VARCHAR(150) NULL,
  familia             VARCHAR(100) NOT NULL,
  tipo_especimen      VARCHAR(80)  NULL,
  fecha_recoleccion   DATE         NOT NULL,
  investigador        VARCHAR(100) NOT NULL,
  departamento        VARCHAR(60)  NOT NULL,
  estado_conservacion VARCHAR(20)  NOT NULL,
  -- Categoria UICN original (CR, EN, VU, NT, LC, DD, NE) si el investigador la informo.
  categoria_uicn      VARCHAR(2)   NULL,
  descripcion         VARCHAR(5000) NULL,
  -- Valores de los atributos personalizados: {"temperatura_agua": 12.5, "canto": "..."} (ASR5)
  atributos           JSON         NULL,
  plantilla_version   INT          NULL,
  anulado_en          DATETIME(3)  NULL,
  creado_en           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  actualizado_en      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_hallazgo PRIMARY KEY (id),
  CONSTRAINT ck_hallazgo_version CHECK (version >= 1),
  CONSTRAINT ck_hallazgo_estado CHECK (estado_conservacion IN ('EN_PELIGRO','NO_AMENAZADA','SIN_EVALUAR')),
  CONSTRAINT ck_hallazgo_uicn CHECK (categoria_uicn IS NULL OR categoria_uicn IN ('CR','EN','VU','NT','LC','DD','NE')),
  CONSTRAINT ck_hallazgo_nombre CHECK (CHAR_LENGTH(TRIM(nombre_cientifico)) > 0),
  CONSTRAINT ck_hallazgo_familia CHECK (CHAR_LENGTH(TRIM(familia)) > 0),
  CONSTRAINT ck_hallazgo_investigador CHECK (CHAR_LENGTH(TRIM(investigador)) > 0),
  CONSTRAINT ck_hallazgo_departamento CHECK (CHAR_LENGTH(TRIM(departamento)) > 0),
  CONSTRAINT ck_hallazgo_atributos CHECK (atributos IS NULL OR JSON_TYPE(atributos) = 'OBJECT'),
  CONSTRAINT ck_hallazgo_plantilla CHECK (plantilla_version IS NULL OR plantilla_version >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_hallazgo_especie_fecha      ON hallazgo (nombre_cientifico, fecha_recoleccion DESC);
CREATE INDEX ix_hallazgo_investigador_fecha ON hallazgo (investigador, fecha_recoleccion DESC);
CREATE INDEX ix_hallazgo_estado_fecha       ON hallazgo (estado_conservacion, fecha_recoleccion DESC);

-- ---------------------------------------------------------------------------
-- Historial inmutable (HU-05, ASR7): una fila por version, append-only.
-- Sin UPDATE ni DELETE: la inmutabilidad se refuerza con permisos de BD (doc 05 seccion 11)
-- y con los triggers de mas abajo, para que no dependa unicamente del codigo.
-- ---------------------------------------------------------------------------
CREATE TABLE hallazgo_version (
  id                  BINARY(16)   NOT NULL,
  hallazgo_id         BINARY(16)   NOT NULL,
  version             INT          NOT NULL,
  -- Snapshot completo del hallazgo en esa version (los mismos campos publicos del evento).
  snapshot            JSON         NOT NULL,
  -- Obligatoria desde la version 2 (HU-03: toda reclasificacion exige justificacion cientifica).
  justificacion       VARCHAR(1000) NULL,
  autor               VARCHAR(100) NOT NULL,
  creado_en           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_hallazgo_version PRIMARY KEY (id),
  CONSTRAINT uq_hallazgo_version UNIQUE (hallazgo_id, version),
  CONSTRAINT fk_hallazgo_version_hallazgo FOREIGN KEY (hallazgo_id)
    REFERENCES hallazgo (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_hallazgo_version_num CHECK (version >= 1),
  CONSTRAINT ck_hallazgo_version_autor CHECK (CHAR_LENGTH(TRIM(autor)) > 0),
  CONSTRAINT ck_hallazgo_version_snapshot CHECK (JSON_TYPE(snapshot) = 'OBJECT'),
  -- Desde la version 2 la justificacion es obligatoria y no puede ser vacia.
  CONSTRAINT ck_hallazgo_version_justificacion CHECK (
    version = 1 OR (justificacion IS NOT NULL AND CHAR_LENGTH(TRIM(justificacion)) > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_hallazgo_version_hallazgo ON hallazgo_version (hallazgo_id, version DESC);

-- ---------------------------------------------------------------------------
-- Auditoria campo a campo (HU-05): valor previo, valor nuevo, justificacion, usuario y fecha.
-- Tambien append-only.
-- ---------------------------------------------------------------------------
CREATE TABLE hallazgo_auditoria (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  hallazgo_id     BINARY(16)   NOT NULL,
  version_previa  INT          NULL,
  version_nueva   INT          NOT NULL,
  campo           VARCHAR(60)  NOT NULL,
  valor_previo    VARCHAR(1000) NULL,
  valor_nuevo     VARCHAR(1000) NULL,
  justificacion   VARCHAR(1000) NULL,
  autor           VARCHAR(100) NOT NULL,
  creado_en       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_hallazgo_auditoria PRIMARY KEY (id),
  CONSTRAINT fk_hallazgo_auditoria_hallazgo FOREIGN KEY (hallazgo_id)
    REFERENCES hallazgo (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_hallazgo_auditoria_version CHECK (version_nueva >= 1),
  CONSTRAINT ck_hallazgo_auditoria_campo CHECK (CHAR_LENGTH(TRIM(campo)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_hallazgo_auditoria_hallazgo ON hallazgo_auditoria (hallazgo_id, creado_en DESC);

-- ---------------------------------------------------------------------------
-- Evidencias del hallazgo (HU-01: fotografias y audios).
-- Solo METADATOS y la ruta del archivo; el binario vive en el volumen de archivos.
-- ---------------------------------------------------------------------------
CREATE TABLE hallazgo_evidencia (
  id            BINARY(16)   NOT NULL,
  hallazgo_id   BINARY(16)   NOT NULL,
  nombre        VARCHAR(255) NOT NULL,
  tipo          VARCHAR(20)  NOT NULL,
  content_type  VARCHAR(100) NOT NULL,
  tamano_bytes  BIGINT       NOT NULL,
  ruta          VARCHAR(500) NOT NULL,
  sha256        CHAR(64)     NOT NULL,
  creado_en     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

  CONSTRAINT pk_hallazgo_evidencia PRIMARY KEY (id),
  CONSTRAINT fk_hallazgo_evidencia_hallazgo FOREIGN KEY (hallazgo_id)
    REFERENCES hallazgo (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT uq_hallazgo_evidencia_sha UNIQUE (hallazgo_id, sha256),
  CONSTRAINT ck_hallazgo_evidencia_tipo CHECK (tipo IN ('FOTOGRAFIA','AUDIO')),
  CONSTRAINT ck_hallazgo_evidencia_tamano CHECK (tamano_bytes > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX ix_hallazgo_evidencia_hallazgo ON hallazgo_evidencia (hallazgo_id);
