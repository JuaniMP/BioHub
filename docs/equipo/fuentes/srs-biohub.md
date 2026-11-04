Proyecto Semestral BioHub
Especificación de Requerimientos de Software SRS
Grupo 5:Nicolas Espinosa Estrada, Juanita Mejía Pineda, Hernando Javier García Mogollón y Santiago Pinzón VásquezUniversidad El Bosque

1. INTRODUCCION

1.1 PROPOSITO
El presente documento describe la ESPECIFICACIÓN DE REQUERIMIENTOS DE SOFTWARE PARA LA APLICACIÓN BioHub. Este documento debe ser aprobado por los clientes, ya que es una descripción completa y global del funcionamiento del Sistema. Por otro lado, esta especificación debe ser utilizada por los analistas para producir la documentación de diseño.

1.2 RESUMEN DE PRODUCTO Y ALCANCE DEFINIDO (PROBLEMA PLANTEADO)
Visión
El Instituto Nacional de Biodiversidad enfrenta un problema serio: la información que recogen las expediciones biológicas del país vive dispersa en hojas de cálculo, correos y discos duros, y cuando estalla una crisis ecológica nadie logra reunir los datos a tiempo. Por eso encargó la construcción de BioHub, una plataforma que centralice, proteja y divulgue el inventario nacional de flora y fauna.
BioHub debe convertirse en la única fuente confiable de información para los investigadores de campo, los directores científicos y los organismos internacionales que toman decisiones a partir de estos datos.
Cómo funciona y qué lo hace difícil
El regreso del investigador. Los biólogos pasan meses en campo recogiendo ejemplares y, al volver a una estación base, cargan sus hallazgos en BioHub. Ningún registro se parece a otro: una rana endémica exige la temperatura del agua y una grabación acústica de su canto, mientras que un ave migratoria requiere rutas de GPS y una muestra de ADN. El sistema tiene que permitir que estas fichas taxonómicas sean flexibles, porque los atributos que hoy se miden para una especie pueden cambiar el próximo año. Además, cada equipo físico que sale a campo (drones, cámaras trampa, hidrófonos y sensores) es un activo del Estado, y debe quedar siempre asociado al investigador responsable para que no se pierda.
La consulta viral. BioHub también es una ventana pública. Se ha visto que, cuando se anuncia el descubrimiento de una especie exótica, el tráfico hacia esa ficha en particular se multiplica por más de sesenta en cuestión de minutos, empujado por la prensa y las universidades. En el pasado, esos picos tumbaban la base de datos y dejaban a los investigadores en zonas remotas sin poder subir sus hallazgos. El Instituto exige que la consulta pública sea instantánea y que jamás compita por recursos con la carga de datos científicos.
La ciencia se corrige. Un ejemplar que hace tres meses se registró como una especie puede reclasificarse como otra distinta después de un análisis genético. BioHub no puede simplemente borrar lo anterior: cualquier auditor científico debe poder reconstruir la historia completa de un hallazgo, saber qué se creía en enero y compararlo con lo que se sabe en diciembre, y ver quién autorizó cada cambio y por qué.
El corredor biológico. La ubicación exacta de ciertas especies en peligro es información delicada: si esas coordenadas llegan a manos de cazadores ilegales, el sistema habrá fallado en lo más importante. Por eso, un visitante común solo puede ver la región o el departamento de un hallazgo, y únicamente el personal científico de alto rango puede ver el mapa con las coordenadas exactas. Esa validación de permisos tiene que ser infalible y estar centralizada en un solo lugar.
BioHub como plataforma abierta. El Instituto no es el único que usa estos datos: universidades del mundo y ONGs los consumen por internet para alimentar sus propios modelos climáticos. Hace falta un punto de entrada controlado que proteja la red interna del instituto, capaz de limitar cuántas peticiones puede hacer cada consumidor externo por minuto, para que un script mal escrito no agote el ancho de banda. Y si algún día el instituto decide mover sus servidores internos de lugar, los usuarios externos no deberían notar ningún cambio en la forma de conectarse.
Los reportes que ahogan al sistema. Cada mes, el Instituto genera índices de presión sobre los ecosistemas, cruzando millones de registros para detectar zonas de deforestación. Ese análisis masivo suele dejar al sistema sin memoria y con el procesador al tope. El diseño debe garantizar que esos reportes pesados se generen de forma independiente, de modo que el registro diario de avistamientos nunca se vuelva lento por culpa de la analítica que corre por detrás.

2. REQUERIMIENTOS ESPECIFICOS

2.1 Requerimientos Funcionales 
1. Gestión de Registros y Hallazgos
RF1: El sistema debe permitir a los investigadores registrar los hallazgos biológicos y ejemplares recolectados en campo, incluyendo evidencia adjunta (fotografías, grabaciones de audio y archivos de datos asociados).
RF2: El sistema debe permitir la creación de fichas taxonómicas con esquemas flexibles, de modo que cada especie pueda registrar atributos diferentes (ej. temperatura del agua, grabaciones acústicas, rutas GPS, muestras de ADN).
RF3: El sistema debe permitir modificar o agregar nuevos atributos medibles a las fichas taxonómicas a lo largo del tiempo.
RF4: El sistema debe permitir la reclasificación o modificación de los datos de un ejemplar previamente registrado (ej. actualizar la especie tras un análisis genético).
2. Gestión de Equipos de Campo
RF5: El sistema debe permitir el registro de los diferentes equipos físicos utilizados en las expediciones (drones, cámaras trampa, hidrófonos, sensores).
RF6: El sistema debe asociar obligatoriamente cada equipo físico registrado al investigador responsable de su uso.
3. Trazabilidad y Auditoría
RF7: El sistema debe mantener un historial completo e inmutable de todos los cambios realizados en el registro de un hallazgo.
RF8: El sistema debe permitir la consulta de las versiones históricas de un registro para comparar la información en diferentes periodos de tiempo.
RF9: El sistema debe registrar y mostrar qué usuario (auditor/investigador) autorizó cada modificación en una ficha y el motivo o justificación del cambio.
4. Consulta y Visualización (Portal Público y Privado)
RF10: El sistema debe contar con un módulo de consulta pública para que visitantes, prensa e instituciones puedan buscar y visualizar las fichas de las especies.
RF11: El sistema debe restringir la visualización de la ubicación de especies en peligro, mostrando únicamente la región o el departamento a los visitantes comunes.
RF12: El sistema debe permitir la visualización de un mapa con las coordenadas exactas de los hallazgos única y exclusivamente al personal científico de alto rango.
RF13: El sistema debe validar los permisos de acceso y roles de usuario de forma centralizada al momento de solicitar datos de ubicación.
5. Integración Externa (API)
RF14: El sistema debe proporcionar un punto de entrada (API/Web Services) para que consumidores externos (universidades y ONGs) puedan consultar y extraer los datos de biodiversidad.
6. Generación de Reportes
RF15: El sistema debe generar reportes mensuales que calculen los índices de presión sobre los ecosistemas.
RF16: El sistema debe permitir el cruce de registros masivos dentro de la generación de reportes para detectar zonas de deforestación.
7. Gestión de usuarios
RF17: El sistema debe permitir registrar usuarios internos y asignarles uno de los siguientes roles: investigador de campo, auditor científico, personal científico de alto rango, analista ambiental o administrador.
RF18: El sistema debe autenticar a todo usuario interno mediante credenciales (usuario/contraseña o mecanismo equivalente) antes de permitir el acceso a los módulos de carga de datos, auditoría o visualización de coordenadas exactas.}
RF19: El sistema debe permitir a un administrador modificar o revocar el rol de un usuario existente, y ese cambio debe reflejarse de inmediato en la validación centralizada de permisos definida en RF13.

2.2 Requerimientos No Funcionales
1. Disponibilidad
RNF1: Ante un pico de tráfico de 60 veces o más el promedio habitual sobre el módulo de consulta pública, el sistema debe mantener al menos el 95% de las peticiones respondidas exitosamente (sin error de servidor), aunque funciones secundarias (como filtros avanzados de búsqueda o recomendaciones relacionadas) se desactiven temporalmente, con tiempo de estabilización menor a 5 minutos.
RNF2: El módulo de carga de datos científicos debe mantener un tiempo de respuesta menor o igual a 5 segundos, aunque el módulo de consulta pública esté bajo un pico de tráfico de hasta 60 veces lo habitual.
RNF3: La generación del reporte mensual de presión sobre ecosistemas no debe dejar indisponible el registro diario de avistamientos, ni degradar su tiempo de respuesta en más del 10%.
2. Rendimiento
RNF4: El registro de un hallazgo biológico con archivos asociados debe completarse en 5 segundos o menos, bajo condiciones normales.
RNF5: El módulo de consulta pública debe responder en 2 segundos o menos, incluso con carga científica simultánea corriendo.
3. Usabilidad
RNF6: Un usuario nuevo debe aprender a usar las funciones principales (buscar ficha, registrar hallazgo) en menos de 5 minutos, en máximo 3 pasos, con tasa de error menor o igual al 5%.
RNF7: Cuando un visitante público consulta el hallazgo de una especie en peligro, al menos el 90% de los usuarios en pruebas de usabilidad debe poder identificar correctamente por qué no ve las coordenadas exactas, sin necesidad de ayuda externa.
4. Modificabilidad
RNF8: Un biólogo debe poder agregar o editar un campo personalizado en la ficha de una especie en menos de 2 minutos y sin ayuda técnica, con una tasa de éxito mayor o igual al 90% en pruebas de usabilidad.
5. Seguridad
RNF9: El sistema debe almacenar cifradas con AES-256 (o equivalente) las coordenadas exactas de hallazgos de especies en peligro, sin que queden persistidas en texto plano, para evitar accesos no autorizados que las expongan.
RNF10: El sistema debe garantizar que el historial de cambios de un hallazgo sea inmutable (append-only): cualquier intento de actualización o eliminación física sobre un registro histórico debe ser rechazado, sin excepción de rol, permitiendo reconstruir la evolución completa del hallazgo.
6. Interoperabilidad y Portabilidad
RNF11: La API pública debe limitar por defecto a 60 peticiones por minuto por consumidor externo, con la opción de que el administrador configure ese límite por consumidor.
RNF12: Ante una migración de infraestructura interna, los consumidores externos no requieren cambios en su forma de conexión.
RNF13: Portal público y API accesibles desde las últimas dos versiones de navegadores estándar: Chrome, Firefox, Edge, Safari.
 
ASR (Architecturally Significant Requirement)
1. Disponibilidad
ASR1 (RNF1): Cuando el público general genera un pico de tráfico de 60 veces o más el promedio habitual sobre el módulo de consulta pública, tras el anuncio de una especie exótica, el sistema mantiene al menos el 95% de peticiones respondidas exitosamente, desactivando funciones secundarias, con tiempo de estabilización menor a 5 minutos.
Justificación:
Argumento: El sistema exige que el portal público jamás compita por recursos con la carga de datos de los científicos. Para lograrlo, se adopta una arquitectura basada en servicios en la que el módulo público y el módulo interno se despliegan como servicios separados, cada uno con sus propios recursos.
Por qué es un ASR: Si este requerimiento no existiera, todo funcionaría en un monolito simple. Al existir, nos obliga a cambiar la topología de despliegue para poder escalar los servicios de forma independiente frente a los picos de tráfico.

ASR2 (RNF2): Cuando investigadores en campo generan cargas de datos científicos mientras el módulo público está bajo un pico de tráfico de hasta 60 veces lo habitual, el sistema mantiene el módulo de carga con un tiempo de respuesta menor o igual a 5 segundos.
Justificación:
Argumento: Para garantizar que los investigadores puedan cargar datos en menos de 5 segundos mientras el portal público está saturado, el módulo de carga no puede compartir procesamiento, hilos de ejecución ni base de datos de escritura con el ecosistema público. 
Por qué es un ASR: Refuerza la decisión arquitectónica del ASR1; obliga a aislar el módulo interno en un servicio desplegado por separado, garantizando recursos de cómputo exclusivos que no se ven afectados por eventos externos.


ASR3 (RNF3): Cuando el sistema genera el reporte mensual de presión sobre ecosistemas, cruzando millones de registros, el registro diario de avistamientos permanece disponible con un incremento de tiempo de respuesta no mayor al 10%.
Justificación:
Argumento: Analizar millones de registros para generar reportes consume demasiada memoria y CPU, lo cual amenaza la operación transaccional diaria. 
Por qué es un ASR: Obliga a diseñar un procesamiento asíncrono basado en colas o a separar la base de datos en dos instancias físicas distintas (una analítica para reportes y una transaccional para escrituras).

2.Rendimiento 
ASR4 (RNF5): Cuando visitantes consultan el módulo público mientras hay carga científica simultánea, el sistema responde en 2 segundos o menos.
Justificación:
Argumento: Las consultas públicas deben ser instantáneas sin importar si, al mismo tiempo, los científicos están subiendo información pesada. 
Por qué es un ASR: Exige implementar un motor de caché distribuido en la arquitectura o utilizar un patrón de separación de lecturas y escrituras (como CQRS) para servir los datos sin bloquear la base principal.

3.Modificabilidad 
ASR5 (RNF8): Cuando un biólogo necesita registrar una especie con atributos no estandarizados, el sistema le permite agregar un campo personalizado a la ficha en menos de 2 minutos y sin soporte técnico, sin afectar los registros ya existentes de otras especies.
Justificación:
Argumento: Los investigadores necesitan agregar atributos a las fichas biológicas de forma flexible sin alterar registros pasados ni requerir desarrollo de software. 
Por qué es un ASR: Descarta los esquemas relacionales rígidos y dicta la adopción de bases de datos NoSQL (orientadas a documentos), el uso de columnas de tipo JSON dentro de una base de datos relacional o la implementación de un patrón estructural avanzado como EAV (Entity-Attribute-Value).

4. Seguridad 
ASR6 (RNF9): Cuando el sistema almacena las coordenadas de un hallazgo de especie en peligro, en cualquier momento, cifra dicha información con AES-256 o equivalente antes de persistirla.
Justificación:
Argumento: La ubicación de las especies en peligro es crítica y no puede existir en texto plano en la base de datos en ninguna circunstancia. 
Por qué es un ASR: Implica integrar un sistema de gestión de llaves criptográficas (KMS) en la arquitectura, lo cual añade una capa obligatoria de procesamiento en cada lectura y escritura que impacta el diseño general del sistema.

ASR7 (RNF10): Cuando cualquier usuario, incluso con rol administrativo, intenta modificar o eliminar un registro histórico, el sistema rechaza la operación, preservando el 100% del historial sin alteración.
Justificación:
Argumento: El historial científico debe poder reconstruirse de forma exacta, evitando que cualquier rol sobreescriba o borre datos físicamente. 
Por qué es un ASR: Obliga a eliminar las tradicionales operaciones CRUD de actualización y borrado, cambiando el paradigma de persistencia hacia arquitecturas append-only o patrones como Event Sourcing.

5.Integrabilidad 
ASR8 (RNF11): Cuando un consumidor externo, o un script mal escrito, genera un volumen de peticiones excesivo sobre la API pública, el sistema limita las peticiones excedentes a un máximo de 60 por minuto, configurable por el administrador.
Justificación:
Argumento: Es necesario proteger la infraestructura del Instituto para que scripts externos de universidades u ONGs no saturen el ancho de banda. 
Por qué es un ASR: Introduce una pieza obligatoria en la frontera del sistema: un API Gateway o Proxy encargado de interceptar y aplicar políticas de Rate Limiting antes de que el tráfico llegue al backend.

ASR9 (RNF12): Cuando el equipo de infraestructura del Instituto migra los servidores internos, sobre la capa de integración con consumidores externos, en una ventana de mantenimiento, el sistema mantiene el punto de conexión sin alteraciones, con 0 cambios requeridos por los consumidores.
Justificación:
Argumento: Si la infraestructura interna del Instituto cambia de ubicación física, los consumidores externos no deben notar el cambio en sus conexiones. 
Por qué es un ASR: Exige diseñar una capa de abstracción de red robusta (Proxy Inverso) que desacople permanentemente las direcciones IP internas de las URLs públicas expuestas.

3. BACKLOG (HISTORIAS DE USUARIO)
El presente apartado detalla el Product Backlog estructurado mediante Historias de Usuario, el cual define el alcance funcional y técnico para la construcción de la plataforma BioHub. Para el actual ciclo de desarrollo, el equipo de ingeniería asume el firme compromiso de implementar el 100% de las historias documentadas (13 de 13), garantizando una cobertura total de los requerimientos del sistema. Esta decisión de alcance integral incluye la ejecución de la HU-09 (Reportes Mensuales de Presión sobre Ecosistemas), con lo cual se asegura el cumplimiento de todos los requerimientos arquitectónicamente significativos (ASR) definidos para el proyecto. El equipo asume así el reto técnico de desplegar bases de datos independientes (analíticas y transaccionales) y aislar el procesamiento masivo mediante colas de mensajería asíncrona, protegiendo la operación diaria de los científicos. A continuación, se presentan las historias de usuario que conforman el compromiso definitivo del Sprint.
ID: HU-01
Título: Registro de Hallazgos Biológicos en Campo
Como: Investigador biológico de campo
Quiero: registrar un nuevo espécimen o hallazgo recolectado durante una expedición
Para que: se centralice la evidencia recolectada y se evite la pérdida de información científica sensible.
Escala (Fibonacci): 5 puntos
Criterios de aceptación:
El formulario de ingreso debe validar obligatoriamente los campos: nombre genérico, fecha de recolección, investigador responsable y tipo de espécimen.
Permitir la carga de archivos adjuntos como fotografías del espécimen o grabaciones de audio asociadas.
Si faltan campos obligatorios, el sistema debe denegar el registro y mostrar una alerta descriptiva ("Campos obligatorios incompletos").

• ID: HU-02
Título: Configuración de Fichas Taxonómicas con Atributos Flexibles
Como: Investigador biológico
Quiero: crear y modificar los campos medibles de una ficha taxonómica de acuerdo con las necesidades de cada especie (ej. temperatura de agua, ADN, cantos)
Para que: la captura de datos se adapte dinámicamente a los cambios y descubrimientos de la investigación a lo largo del tiempo.
Escala (Fibonacci): 8 puntos
Criterios de aceptación:
El usuario con permisos científicos debe poder agregar, renombrar o deshabilitar atributos personalizados en la plantilla de una ficha sin modificar código de programación.
El formulario de registro para esa especie debe renderizar y validar dinámicamente los nuevos campos asignados.
La adición o modificación de atributos en una plantilla no debe alterar ni corromper los registros históricos de hallazgos ya guardados bajo esquemas previos.

• ID: HU-03 
Título: Reclasificación Científica de Hallazgos Existentes
Como: Investigador biológico
Quiero: modificar y reclasificar los datos de un ejemplar previamente registrado (por ejemplo, corregir la especie tras recibir pruebas de laboratorio)
Para que: el inventario nacional de biodiversidad siempre refleje la verdad científica más actualizada.
Escala (Fibonacci): 3 puntos
Criterios de aceptación:
El sistema debe permitir editar campos taxonómicos clave de un hallazgo ya existente si el usuario cuenta con el rol científico correspondiente.
Al guardar la modificación, el sistema debe solicitar obligatoriamente una justificación científica de la reclasificación antes de aplicar los cambios.

• ID: HU-04
Título: Asignación Obligatoria de Activos y Equipos de Expedición
Como: Administrador del sistema
Quiero: asociar de manera obligatoria cada activo físico asignado a campo (ej. drones, cámaras trampa) con un investigador responsable activo
Para que: el Instituto mantenga un control e inventario riguroso de los recursos públicos en las expediciones y se eviten pérdidas.
Escala (Fibonacci): 3 puntos
Criterios de aceptación:
Al registrar o asignar un activo, el sistema debe validar que el custodio sea un investigador registrado y activo en el sistema.
La base de datos debe actualizar el estado del activo como "En Expedición" vinculando físicamente el ID del investigador responsable.
El módulo de inventario debe contar con una vista rápida que permita buscar y filtrar qué equipos tiene bajo custodia cada investigador en tiempo real.

• ID: HU-05 
Título: Auditoría Histórica de Cambios y Versiones
Como: Auditor científico
Quiero: visualizar el historial inmutable de cambios y comparar las versiones de una muestra biológica a lo largo del tiempo
Para que: se garantice la total trazabilidad, transparencia científica e inmutabilidad de los descubrimientos del inventario nacional.
Escala (Fibonacci): 5 puntos 
Criterios de aceptación:
Cada vez que se altere un dato en una ficha taxonómica, se debe registrar automáticamente en una tabla de auditoría el valor previo, el nuevo valor, la justificación científica obligatoria, el usuario ejecutor y la fecha/hora.
El sistema debe bloquear físicamente cualquier instrucción de actualización o eliminación directa sobre las tablas de auditoría.
El auditor debe tener acceso a una herramienta de comparación de versiones para resaltar diferencias taxonómicas entre registros históricos.

• ID: HU-06 
Título: Portal de Consulta Pública de Especies con Ubicación Protegida
Como: Visitante común
Quiero: buscar y visualizar las fichas de las especies del catálogo público
Para que: pueda informarme de la biodiversidad del país sin comprometer la ubicación de especies en peligro frente a cazadores ilegales.
Escala (Fibonacci): 5 puntos 
Criterios de aceptación:
1. El visitante común debe poder buscar especies por nombre científico, familia o departamento.
2. Si la especie consultada se encuentra marcada "En Peligro", el portal público debe ocultar las coordenadas GPS exactas y mostrar únicamente la región o departamento general.
3. El portal público debe responder en menos de 2 segundos, incluso si hay procesos paralelos de carga pesada de datos en el backend.

• ID: HU-07 
Título: Mapa Interactivo con Coordenadas Exactas para Personal de Alto Rango
Como: Personal científico de alto rango
Quiero: visualizar un mapa interactivo con la geolocalización precisa de los avistamientos y hallazgos biológicos
Para que: pueda realizar análisis biológicos de distribución geográfica de manera segura.
Escala (Fibonacci): 8 puntos 
Criterios de aceptación:
El mapa interactivo solo debe renderizar marcadores con coordenadas GPS precisas si el token del usuario tiene asignado y validado el rol de "Personal científico de alto rango".
La validación de permisos de acceso debe realizarse de forma centralizada en el backend en cada petición al servicio de mapas.
El sistema debe desencriptar en memoria las coordenadas únicamente para los usuarios aprobados.

• ID: HU-08
Título: Endpoint de API de Biodiversidad con Rate Limiting
Como: Desarrollador externo de universidades u ONG´s
Quiero: consumir datos de biodiversidad mediante servicios web expuestos con limitación de peticiones configurada
Para que: podamos integrar la información científica a nuestros proyectos de investigación sin comprometer la disponibilidad de la base de datos del Instituto.
Escala (Fibonacci): 8 puntos
Criterios de aceptación:
El consumidor externo debe autenticarse utilizando un encabezado HTTP con una API Key válida.
Si el consumidor excede el límite de peticiones configurado por minuto, el sistema debe responder con un código de estado HTTP 429 sin afectar al resto del sistema.
Cualquier cambio o migración de la infraestructura de red interna del Instituto debe ser transparente para el consumidor externo, manteniendo inalteradas las URLs expuestas de la API.

• ID: HU-09
Título: Reportes Mensuales de Presión sobre Ecosistemas y Deforestación
Como: Analista ambiental
Quiero: programar y generar reportes mensuales de análisis de presión ecológica que crucen masivamente millones de registros almacenados
Para que: se puedan detectar zonas con alerta de deforestación de manera oportuna sin sobrecargar el servicio de registro diario de los científicos de campo.
Escala (Fibonacci): 13 puntos
Criterios de aceptación:
La generación del reporte mensual se debe delegar a un proceso asíncrono en segundo plano para evitar el bloqueo del navegador del analista.
La ejecución de este procesamiento pesado de datos no debe incrementar los tiempos de respuesta del registro transaccional diario de avistamientos en más de un 10%.
El reporte final debe estar disponible para descarga en formatos estructurados estándar como PDF y CSV.
• ID: HU-10:
Título: Registro y Asignación de Roles de Usuario
Como: Administrador del sistema
Quiero: registrar nuevos usuarios internos y asignarles un rol (investigador, auditor científico, personal científico de alto rango, analista ambiental o administrador)
Para que: cada usuario acceda únicamente a las funciones y datos correspondientes a su nivel de autorización.
Escala (Fibonacci): 5 puntos
Criterios de aceptación:
1.El sistema debe validar que cada usuario nuevo tenga exactamente un rol asignado del catálogo definido.
2.Un usuario sin rol asignado no debe poder acceder a ningún módulo distinto del portal público.
3.El administrador debe poder modificar el rol de un usuario existente, y el cambio debe aplicarse desde la siguiente petición del usuario afectado.

• ID: HU-11:
Título: Autenticación Segura de Usuarios Internos
Como: Usuario interno (investigador, auditor o personal científico)
Quiero: autenticarme con mis credenciales antes de acceder a los módulos internos
Para que: mi identidad quede verificada y mis acciones queden correctamente asociadas a mi usuario en la auditoría.
Escala (Fibonacci): 3 puntos
Criterios de aceptación:
1.El sistema debe rechazar el acceso a cualquier módulo interno sin una sesión autenticada válida.
2.Tres intentos fallidos consecutivos de autenticación deben bloquear temporalmente la cuenta.
3.Toda acción de carga, modificación o reclasificación debe registrar el ID del usuario autenticado que la ejecutó (soporte directo de RF9).
• ID: HU-12:
Título: Emisión y Revocación de API Keys para Consumidores Externos
Como: Administrador del Instituto
Quiero: generar y revocar credenciales (API Key) para universidades y ONGs
Para que: solo consumidores autorizados puedan consultar la API pública y pueda cortarse el acceso a quien abuse del servicio.
Escala (Fibonacci): 5 puntos
Criterios de aceptación:
1.El sistema debe permitir generar una API Key única asociada a un consumidor externo identificado.
2.El sistema debe permitir revocar una API Key existente, rechazando de inmediato cualquier petición futura que la use.
3.Toda petición a la API pública sin una API Key válida debe rechazarse con código HTTP 401.

• ID: HU-13:
Título: Búsqueda y Filtrado Interno de Hallazgos
Como: Investigador biológico
Quiero: buscar y filtrar los hallazgos registrados por especie, fecha, investigador o ubicación
Para que: pueda ubicar y dar seguimiento rápidamente a los registros relevantes para mi investigación, sin depender del portal público.
Escala (Fibonacci): 5 puntos
Criterios de aceptación:
1.El sistema debe permitir combinar al menos dos filtros simultáneos (por ejemplo, especie y rango de fechas).
2.Los resultados deben incluir coordenadas exactas únicamente si el rol del usuario que consulta lo permite (ver RF12/RF13).
3.La búsqueda debe responder en menos de 3 segundos incluso con un volumen alto de registros indexados.

Compromiso:
El equipo de ingeniería se compromete formalmente a entregar el 100% de las historias de usuario documentadas, abarcando la totalidad del esfuerzo estimado para el proyecto (76 puntos Fibonacci). Esta ejecución integral garantiza el cumplimiento absoluto de los requerimientos arquitectónicamente significativos (ASR) exigidos. Al materializar este alcance total mediante una Arquitectura Basada en Servicios, bases de datos especializadas y procesamiento asíncrono, se dotará al Instituto Nacional de Biodiversidad de una plataforma resiliente, capaz de proteger datos sensibles y soportar picos masivos de tráfico. Cabe precisar que la generación de reportes ecosistémicos (HU-09) se implementará a una menor escala operativa ajustada al entorno del prototipo, validando de igual forma el aislamiento arquitectónico necesario para ejecutar este análisis pesado sin afectar la velocidad ni la disponibilidad de la operación transaccional diaria.
4. WBS DEL PROYECTO

Estructura de Desglose de Trabajo (WBS) - BioHub
1. Gestión y Planificación del Proyecto
  1.1 Definición de alcance y objetivos del proyecto
  1.2 Elaboración del cronograma y asignación de roles del equipo
  1.3 Identificación y gestión de riesgos
  1.4 Seguimiento y control (actas, reuniones, entregables por hito)
2. Especificación de Requerimientos (Entrega 1)
  2.1 Análisis del enunciado y levantamiento de necesidades
  2.2 Definición de requerimientos funcionales
  2.3 Definición de requerimientos no funcionales y ASR
  2.4 Elaboración del backlog (historias de usuario)
  2.5 Redacción del SRS (formato Sommerville) y validación con el cliente
3. Definición de Arquitectura (Entrega 2)
  3.1 Análisis de atributos de calidad y drivers arquitectónicos (ASR)
  3.2 Evaluación y selección del estilo/patrón arquitectónico
  3.3 Modelado C4 (Contexto, Contenedores, Componentes)
  3.4 Documentación y justificación de decisiones arquitectónicas (ADR)
4. Diseño (Entrega 2)
  4.1 Diseño del modelo de datos y esquema flexible de fichas taxonómicas
  4.2 Diagramas UML (casos de uso, clases, secuencia)
  4.3 Diseño de patrones de solución (control de acceso, caché, colas/procesamiento asíncrono, rate limiting, API Gateway)
  4.4 Diseño de interfaces (portal público y módulo interno)
  4.5 Revisión y validación del diseño con el equipo
5. Implementación (Entrega 3)
  5.1 Configuración del entorno de desarrollo y CI/CD
  5.2 Módulo de gestión de hallazgos y fichas taxonómicas
  5.3 Módulo de gestión de equipos y activos de campo
  5.4 Módulo de auditoría e historial inmutable
  5.5 Portal de consulta pública
  5.6 Módulo de mapas y control de acceso a coordenadas
  5.7 API pública / Gateway con autenticación y rate limiting
  5.8 Módulo de generación de reportes asíncronos
  5.9 Módulo de usuarios, roles y autenticación
6. Pruebas y Aseguramiento de Calidad
  6.1 Pruebas unitarias
  6.2 Pruebas de integración
  6.3 Pruebas de carga y rendimiento (picos de tráfico x60)
  6.4 Pruebas de seguridad (cifrado, control de acceso, auditoría inmutable)
  6.5 Pruebas de usabilidad
7. Despliegue y Cierre del Proyecto
  7.1 Despliegue a ambiente de producción/demo
  7.2 Documentación final del sistema
  7.3 Preparación y sustentación
  7.4 Retrospectiva y lecciones aprendidas


Estructura de Desglose de Trabajo (WBS) - BioHub
Apoyo visual



Bio hub WBS.drawio
*Archivo adjunto para evitar problemas con la legibilidad de la imagen
