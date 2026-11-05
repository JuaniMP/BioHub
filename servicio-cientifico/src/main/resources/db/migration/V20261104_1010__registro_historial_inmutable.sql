-- HU-05: "bloquear fisicamente cualquier instruccion de actualizacion o eliminacion directa
-- sobre las tablas de auditoria". Segunda barrera, ademas de los permisos del usuario de
-- aplicacion (doc 05 seccion 11: solo INSERT y SELECT sobre historial) y del codigo.
-- Asi la inmutabilidad (ASR7) no depende unicamente de la capa Java.
--
-- NOTA devops: crear triggers exige el privilegio TRIGGER del usuario de migracion.
-- Si el proveedor administrado no lo concede, esta migracion se omite y la inmutabilidad
-- queda sostenida por los GRANT (ver docs/equipo/09-despliegue.md).

CREATE TRIGGER trg_hallazgo_version_no_update
BEFORE UPDATE ON hallazgo_version
FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'hallazgo_version es append-only: UPDATE no permitido';

CREATE TRIGGER trg_hallazgo_version_no_delete
BEFORE DELETE ON hallazgo_version
FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'hallazgo_version es append-only: DELETE no permitido';

CREATE TRIGGER trg_hallazgo_auditoria_no_update
BEFORE UPDATE ON hallazgo_auditoria
FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'hallazgo_auditoria es append-only: UPDATE no permitido';

CREATE TRIGGER trg_hallazgo_auditoria_no_delete
BEFORE DELETE ON hallazgo_auditoria
FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'hallazgo_auditoria es append-only: DELETE no permitido';
