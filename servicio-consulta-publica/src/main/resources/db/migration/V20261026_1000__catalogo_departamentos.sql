
-- Catalogo de referencia (NO es dato de prueba). Copia propia del servicio: no se comparte BD con cientifico.
-- Region desnormalizada como texto: el catalogo es pequeno, inmutable y solo se lee.
CREATE TABLE departamento (
  codigo_dane VARCHAR(2)  NOT NULL,
  nombre      VARCHAR(60) NOT NULL,
  region      VARCHAR(40) NOT NULL,
  CONSTRAINT pk_departamento PRIMARY KEY (codigo_dane),
  CONSTRAINT uq_departamento_nombre UNIQUE (nombre),
  CONSTRAINT ck_departamento_codigo CHECK (codigo_dane REGEXP '^[0-9]{2}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO departamento (codigo_dane, nombre, region) VALUES
  ('05','Antioquia','Andina'), ('08','Atlántico','Caribe'), ('11','Bogotá D.C.','Andina'),
  ('13','Bolívar','Caribe'), ('15','Boyacá','Andina'), ('17','Caldas','Andina'),
  ('18','Caquetá','Amazonía'), ('19','Cauca','Pacífica'), ('20','Cesar','Caribe'),
  ('23','Córdoba','Caribe'), ('25','Cundinamarca','Andina'), ('27','Chocó','Pacífica'),
  ('41','Huila','Andina'), ('44','La Guajira','Caribe'), ('47','Magdalena','Caribe'),
  ('50','Meta','Orinoquía'), ('52','Nariño','Pacífica'), ('54','Norte de Santander','Andina'),
  ('63','Quindío','Andina'), ('66','Risaralda','Andina'), ('68','Santander','Andina'),
  ('70','Sucre','Caribe'), ('73','Tolima','Andina'), ('76','Valle del Cauca','Pacífica'),
  ('81','Arauca','Orinoquía'), ('85','Casanare','Orinoquía'), ('86','Putumayo','Amazonía'),
  ('88','San Andrés y Providencia','Insular'), ('91','Amazonas','Amazonía'),
  ('94','Guainía','Amazonía'), ('95','Guaviare','Amazonía'), ('97','Vaupés','Amazonía'),
  ('99','Vichada','Orinoquía');
