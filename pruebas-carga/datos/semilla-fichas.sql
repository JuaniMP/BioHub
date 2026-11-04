-- Semilla SOLO PARA PRUEBAS DE CARGA (no es migración). Se aplica con datos/sembrar.sh y se borra con datos/limpiar.sh.
-- Todas las fichas de carga tienen hallazgo_id que empieza por c0a9a000 (UUID c0a9a000-0000-4000-8000-<12 hex>);
-- los eventos hallazgo.* de carga-cientifica-simultanea.js usan el mismo prefijo, así que un único DELETE los cubre.
-- No toca las fichas reales/de demostración (otro prefijo).
SET SESSION cte_max_recursion_depth = 100000;
SET @total = 2000;

INSERT INTO ficha_publica
  (hallazgo_id, version, nombre_cientifico, nombre_comun, familia, tipo_especimen, fecha_recoleccion,
   estado_conservacion, departamento_codigo, departamento_nombre, region_nombre, descripcion)
WITH RECURSIVE n (i) AS (
  SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < @total
),
deptos AS (
  SELECT codigo_dane, nombre, region, ROW_NUMBER() OVER (ORDER BY codigo_dane) AS rn, COUNT(*) OVER () AS cnt
  FROM departamento
),
generos AS (
  SELECT 0 AS k, 'Atelopus' AS g, 'Bufonidae' AS f UNION ALL SELECT 1, 'Pristimantis', 'Strabomantidae'
  UNION ALL SELECT 2, 'Rhinella', 'Bufonidae' UNION ALL SELECT 3, 'Boana', 'Hylidae'
  UNION ALL SELECT 4, 'Dendropsophus', 'Hylidae' UNION ALL SELECT 5, 'Leptodactylus', 'Leptodactylidae'
  UNION ALL SELECT 6, 'Hyloxalus', 'Dendrobatidae' UNION ALL SELECT 7, 'Oophaga', 'Dendrobatidae'
  UNION ALL SELECT 8, 'Espeletia', 'Asteraceae' UNION ALL SELECT 9, 'Cattleya', 'Orchidaceae'
  UNION ALL SELECT 10, 'Puya', 'Bromeliaceae' UNION ALL SELECT 11, 'Passiflora', 'Passifloraceae'
  UNION ALL SELECT 12, 'Tangara', 'Thraupidae' UNION ALL SELECT 13, 'Anolis', 'Dactyloidae'
  UNION ALL SELECT 14, 'Bothrops', 'Viperidae' UNION ALL SELECT 15, 'Micrurus', 'Elapidae'
  UNION ALL SELECT 16, 'Panthera', 'Felidae' UNION ALL SELECT 17, 'Tremarctos', 'Ursidae'
  UNION ALL SELECT 18, 'Quercus', 'Fagaceae' UNION ALL SELECT 19, 'Podocarpus', 'Podocarpaceae'
)
SELECT UNHEX(CONCAT('c0a9a000', '0000', '4000', '8000', LPAD(HEX(n.i), 12, '0'))),
       1,
       CONCAT(g.g, ' carga', LPAD(n.i, 4, '0')),
       CONCAT('Especie de carga ', g.g, ' ', n.i),
       g.f,
       'Adulto',
       DATE_ADD('2025-01-01', INTERVAL (n.i % 365) DAY),
       CASE WHEN n.i % 5 = 0 THEN 'EN_PELIGRO' WHEN n.i % 5 = 1 THEN 'SIN_EVALUAR' ELSE 'NO_AMENAZADA' END,
       d.codigo_dane, d.nombre, d.region,
       CONCAT('Ficha sintética de prueba de carga número ', n.i, '. Descripción pública sin datos sensibles.')
FROM n
JOIN generos g ON g.k = n.i % 20
JOIN deptos d ON d.rn = (n.i % d.cnt) + 1;

SELECT COUNT(*) AS fichas_de_carga FROM ficha_publica WHERE HEX(hallazgo_id) LIKE 'C0A9A000%';
