-- Catalogo de referencia (NO es dato de prueba). Regiones naturales y departamentos de Colombia.
-- Codigos DANE de 2 digitos. Bogota D.C. se incluye como entidad territorial (11).
-- La asignacion de region es la predominante (p. ej. Cauca figura en Pacifica).

CREATE TABLE ubicacion_region (
  id      TINYINT UNSIGNED NOT NULL,
  nombre  VARCHAR(40)      NOT NULL,
  CONSTRAINT pk_ubicacion_region PRIMARY KEY (id),
  CONSTRAINT uq_ubicacion_region_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE ubicacion_departamento (
  codigo_dane VARCHAR(2)       NOT NULL,
  nombre      VARCHAR(60)      NOT NULL,
  region_id   TINYINT UNSIGNED NOT NULL,
  CONSTRAINT pk_ubicacion_departamento PRIMARY KEY (codigo_dane),
  CONSTRAINT uq_ubicacion_departamento_nombre UNIQUE (nombre),
  CONSTRAINT fk_ubicacion_departamento_region FOREIGN KEY (region_id)
    REFERENCES ubicacion_region (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_ubicacion_departamento_codigo CHECK (codigo_dane REGEXP '^[0-9]{2}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_ubicacion_departamento_region ON ubicacion_departamento (region_id);

INSERT INTO ubicacion_region (id, nombre) VALUES
  (1, 'Andina'), (2, 'Caribe'), (3, 'Pacífica'),
  (4, 'Orinoquía'), (5, 'Amazonía'), (6, 'Insular');

INSERT INTO ubicacion_departamento (codigo_dane, nombre, region_id) VALUES
  ('05', 'Antioquia', 1),
  ('08', 'Atlántico', 2),
  ('11', 'Bogotá D.C.', 1),
  ('13', 'Bolívar', 2),
  ('15', 'Boyacá', 1),
  ('17', 'Caldas', 1),
  ('18', 'Caquetá', 5),
  ('19', 'Cauca', 3),
  ('20', 'Cesar', 2),
  ('23', 'Córdoba', 2),
  ('25', 'Cundinamarca', 1),
  ('27', 'Chocó', 3),
  ('41', 'Huila', 1),
  ('44', 'La Guajira', 2),
  ('47', 'Magdalena', 2),
  ('50', 'Meta', 4),
  ('52', 'Nariño', 3),
  ('54', 'Norte de Santander', 1),
  ('63', 'Quindío', 1),
  ('66', 'Risaralda', 1),
  ('68', 'Santander', 1),
  ('70', 'Sucre', 2),
  ('73', 'Tolima', 1),
  ('76', 'Valle del Cauca', 3),
  ('81', 'Arauca', 4),
  ('85', 'Casanare', 4),
  ('86', 'Putumayo', 5),
  ('88', 'San Andrés y Providencia', 6),
  ('91', 'Amazonas', 5),
  ('94', 'Guainía', 5),
  ('95', 'Guaviare', 5),
  ('97', 'Vaupés', 5),
  ('99', 'Vichada', 4);
