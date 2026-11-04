# Protocolo de prueba de usabilidad: web-publica (RNF6 y RNF7)

## Objetivo
- RNF6 (parte "buscar ficha"): una persona nueva encuentra la ficha de una especie en **3 pasos o menos** y en **menos de 5 minutos**; **tasa de error <= 5 %**.
- RNF7: **>= 90 %** de las personas identifica por qué no ve coordenadas exactas en una ficha.

## Participantes
Mínimo 10 personas sin conocimiento previo del portal, mezcla de edades y de uso de tecnología (ideal: incluir 2 que usen lector de pantalla o solo teclado). No pueden ser parte del equipo. Cada una firma el consentimiento verbal de grabar pantalla (opcional) y no se recogen datos personales: solo un código P01, P02...

## Preparación
- Portal en una URL de pruebas o local (`npm run build && npm run preview`) con el backend de datos de prueba, o la API simulada. Debe haber al menos una especie "En peligro" (ej. Atelopus ebenoides) y una de preocupación menor.
- Dispositivos: la mitad en computador, la otra mitad en celular. Cronómetro, hoja de resultados (abajo).
- Quien modera **no ayuda ni da pistas**; solo lee el guion.

## Guion del moderador
1. "Estamos probando el portal, no a usted. No hay respuestas incorrectas. Piense en voz alta. Puede parar cuando quiera."
2. Preguntas previas: edad aproximada, uso de internet (a diario / semanal / poco), dispositivo.
3. Entregar cada tarea en papel, una a la vez. Iniciar el cronómetro al terminar de leerla; detenerlo cuando la persona diga que terminó o a los 5 minutos.
4. Tras la tarea 3, preguntas de cierre.

### Tareas
| # | Tarea | Éxito | Pasos mínimos |
|---|---|---|---|
| T1 (RNF6) | "Busque la rana *Atelopus ebenoides* y abra su ficha." | Está en la ficha de esa especie | 3 (escribir, buscar, abrir) |
| T2 (RNF6) | "Encuentre una especie de la familia Bufonidae en Boyacá y abra su ficha." | Ficha con familia y departamento correctos | <= 3 con filtros (escribir/elegir, buscar, abrir) |
| T3 (RNF7) | "Ya está en la ficha. Diga en qué lugar exacto se encontró esta rana." (sin indicar que no existe) | Ver siguiente | 0 |

Contar como **paso** cada acción de interfaz: escribir o elegir en un campo, pulsar Buscar, abrir una tarjeta. Contar como **error** cada acción que no acerca a la meta (abrir una ficha equivocada, enviar una búsqueda inválida, volver atrás por confusión, pulsar un elemento que no hace nada). Tasa de error = errores / (errores + acciones correctas) sobre todas las tareas RNF6.

### Preguntas de cierre (después de T3)
- P1: "¿Por qué cree que no aparece el lugar exacto?" (se registra literal).
- P2: "¿Dónde vio la explicación?" (la señala en pantalla).
- P3: "¿Qué tan fácil fue buscar una especie? 1 (muy difícil) a 5 (muy fácil)."
- P4: "¿Algo le confundió o le faltó?"

## Criterios de acierto
- RNF6: T1 y T2 completadas en <= 3 pasos y < 5 min por persona; tasa de error global <= 5 %.
- RNF7 (T3/P1): cuenta como "identifica" si dice, con sus palabras, que es para **proteger la especie** (caza, tráfico, riesgo) o que **es una restricción deliberada de seguridad**. No cuenta: "no cargó", "falta el dato", "no sé". Meta: >= 90 % de participantes.
- Una persona que busca un botón de mapa o coordenadas pero luego lo entiende por el aviso cuenta como acierto si responde bien P1 sin ayuda.

## Plantilla de resultados (una fila por persona y tarea)
| Participante | Dispositivo | Tarea | Éxito (S/N) | Pasos | Tiempo (mm:ss) | Errores | Observaciones |
|---|---|---|---|---|---|---|---|
| P01 | PC | T1 | | | | | |
| P01 | PC | T2 | | | | | |

| Participante | Identifica motivo (S/N) | Respuesta literal P1 | Vio el aviso sin ayuda (S/N) | Facilidad (1-5) | Comentarios |
|---|---|---|---|---|---|
| P01 | | | | | |

## Resumen (completar)
| Métrica | Meta | Resultado | Cumple |
|---|---|---|---|
| Éxito T1 y T2 en <= 3 pasos | 100 % de las tareas completadas | | |
| Tiempo máximo por tarea | < 5 min | | |
| Tasa de error | <= 5 % | | |
| Identifican por qué no hay coordenadas (RNF7) | >= 90 % | | |
| Facilidad media (P3) | informativo | | |

Con menos de 10 participantes, 90 % exige que como máximo falle 0 personas en grupos de 9 o menos; con 10 puede fallar 1. Registrar hallazgos y problemas de usabilidad con severidad (bloqueante, alto, bajo) y su propuesta de mejora.
