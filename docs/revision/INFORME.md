# Informe consolidado: cumplimiento de requisitos (RF, HU, RNF, ASR)

**Fecha:** 2026-11-08 · **Rama:** `feature/ubicacion-consulta-publica` (HEAD `6a5495a`, integra `develop` con el PR #7) · **Autor:** Tech Lead.
**Fuentes:** `requisitos.md` (product-owner), `asr-rnf.md` (arquitecto-software), `07-reporte-qa.md` y `08-reporte-seguridad.md` (iteración 17). La fuente de verdad son `docs/entregas/SRS-BioHub.txt` y `SAD-BioHub.txt`.
**Regla:** Cumple = código + prueba (y medición si el SRS la exige). Parcial = falta prueba, medición o pantalla. No cumple = sin código.

## 1. Resumen ejecutivo

| Grupo | Cumple | Parcial | No cumple | Total |
|---|---:|---:|---:|---:|
| RF (funcionales) | 7 | 1 | 11 | 19 |
| HU (historias) | 1 | 6 | 6 | 13 |
| RNF (no funcionales) | 3 | 6 | 4 | 13 |
| ASR (arquitectónicos) | 3 | 3 | 3 | 9 |

- Lo que existe funciona y está probado: CI verde en `6a5495a` (run 42548966490, 5/5 jobs). En la iteración 17, QA reportó científico 322/0, consulta-pública 239/0 y gateway 168/0, y la prueba E2E por el gateway pasó 50/50. Seguridad aprobó con observaciones, sin Críticos ni Altos.
- **Riesgo principal para la entrega:** `registro` (Hernando), `servicio-equipos` y `servicio-reportes` (Nicolás) siguen siendo esqueletos. Sus ramas remotas (`feature/registro-cientifico`, `feature/reportes-equipos`) no tienen commits fuera de `develop` (verificado con `git rev-list`). Eso deja en No cumple 11 RF, 6 HU, 4 RNF y 3 ASR, entre ellos ASR3, ASR5 y ASR7, que son críticos en el SAD. Además, ningún hallazgo real entra al sistema: portal, mapa y búsqueda solo tienen datos sembrados o eventos simulados.

## 2. Calificación por responsable

| Responsable | RF | HU | RNF/ASR principales | Lectura |
|---|---|---|---|---|
| Juanita (ubicación, consulta pública, web-publica) | 3 Cumple, 1 Parcial (RF12, solo falta montar la pantalla) | 3 Parcial | Cumple: RNF9, ASR6. Parcial por falta de medición k6: ASR1, ASR4, RNF1, RNF5 | Código completo y probado. Falta **medir** (k6 con metadatos D42) y la usabilidad |
| Santiago (gateway, acceso) | 4 Cumple | 1 Cumple, 3 Parcial (faltan pantallas) | Cumple: RNF11, RNF12, ASR8, ASR9 | API completa. Faltan las pantallas de `web-interna` (16j) |
| Hernando (registro) | 7 No cumple | 4 No cumple | ASR5, ASR7, RNF4, RNF8, RNF10 No cumple; ASR2 Parcial | Sin código en el repositorio |
| Nicolás (equipos, reportes) | 4 No cumple | 2 No cumple | ASR3, RNF3 No cumple | Sin código; además, sin `denyAll` |

## 3. Top 10 priorizado (impacto/esfuerzo)

| # | Qué | IDs | Sev. | Resp. | Esf. |
|---|---|---|---|---|---|
| 1 | Módulo `registro`: hallazgos, adjuntos, fichas flexibles y evento real `hallazgo.registrado` | RF1-3, HU-01/02, ASR2, ASR5, RNF4, RNF8 | Crítico | H | L |
| 2 | Historial append-only con versiones y comparador | RF7-9, HU-05, ASR7, RNF10 | Crítico | H | L |
| 3 | `servicio-reportes`: asíncrono (202, cola, worker) y PDF/CSV | RF15-16, HU-09, ASR3, RNF3 | Crítico | N | L |
| 4 | Reclasificación con justificación | RF4, HU-03 | Alto | H | M |
| 5 | `servicio-equipos` con custodio activo | RF5-6, HU-04 | Alto | N | M |
| 6 | Router y menú por rol en `web-interna` con `src/ubicacion/` | RF12, HU-07, HU-13 (16j) | Alto | S | M |
| 7 | Corridas k6 que exige D42 con metadatos (degradación, réplicas, `CIENTIFICA=0/1`, volumen de HU-13) | ASR1, ASR4, RNF1, RNF5, HU-06/13 c.3 | Alto | J | M |
| 8 | Guardar el `sub` del JWT en cada acción (auditoría) | HU-11 c.3, SEC-17 | Alto | H | S |
| 9 | `denyAll` y `@PreAuthorize` en equipos y reportes; SEC-J-19/20 (en curso, 17h) | SEC-02, D55 | Medio | N · J | S |
| 10 | Gateway como punto único de falla: ADR que lo acepte o 2 réplicas con NGINX; usabilidad con 5 personas | SAD §5.1, RNF6/7 | Medio | S + Tech Lead · J | S/M |

## 4. Plan por etapas

- **Rápidas (de Juanita, esta semana):** SEC-J-19/20 (en curso); migrar los `.env` a `cientifico_mq` y `consulta_mq` (SEC-04); el par k6 `CIENTIFICA=0/1` (S); confirmar con el grupo las 4 rutas fuera de la matriz y D55.
- **Importantes (bloquean la entrega):** que Hernando y Nicolás suban su código. Sin `registro` no hay datos reales para medir ASR2/ASR7 ni RNF4. Santiago monta `web-interna` (16j).
- **Largo plazo:** corridas k6 completas sobre `develop` y contra Aiven, con metadatos D42; usabilidad; Edge en CI; ADR o réplicas del gateway.

Detalle por requisito: `requisitos.md` (RF/HU) y `asr-rnf.md` (RNF/ASR y desviaciones del SAD).
