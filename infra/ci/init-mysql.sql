-- Solo CI / pruebas locales de Bruno. Crea las dos bases de la parte de Juanita con usuarios de PRUEBA.
-- Las contraseñas son ficticias y solo existen en el MySQL efimero del job; no se usan en ningun otro lado.
-- (Los scripts de infra/mysql/transaccional se usan en el docker-compose; este es independiente.)
CREATE DATABASE IF NOT EXISTS cientifico       CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS consulta_publica CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS gateway          CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'cientifico_ci'@'%' IDENTIFIED BY 'ci-ficticia-cientifico';
CREATE USER IF NOT EXISTS 'consulta_ci'@'%'   IDENTIFIED BY 'ci-ficticia-consulta';
CREATE USER IF NOT EXISTS 'gateway_ci'@'%'    IDENTIFIED BY 'ci-ficticia-gateway';
GRANT ALL PRIVILEGES ON cientifico.*       TO 'cientifico_ci'@'%';
GRANT ALL PRIVILEGES ON consulta_publica.* TO 'consulta_ci'@'%';
GRANT ALL PRIVILEGES ON gateway.*          TO 'gateway_ci'@'%';
FLUSH PRIVILEGES;
