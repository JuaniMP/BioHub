Proyecto Semestral: BioHub
Sistema Nacional de Registro y Vigilancia de Biodiversidad · Ingeniería de Software 2

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

Qué deben producir

Este es el proyecto que los acompañará todo el semestre y que irán construyendo por entregas:

Entrega 1 (definición y requerimientos): el SRS de BioHub, con los requerimientos funcionales y no funcionales extraídos de este enunciado.

Entrega 2 (arquitectura y diseño): el estilo arquitectónico elegido y justificado, la documentación (UML, Modelo C4) y el diseño con patrones.

Entrega 3 (implementación y cierre): el sistema funcional, con las decisiones de arquitectura aplicadas, las pruebas y la sustentación.

Consideraciones

El SRS de la Entrega 1 debe usar el formato definido en clase, con las consideraciones mínimas de Sommerville.

No inventen alcance de más: quédense en lo que plantea el enunciado. Es mejor una solución acotada y bien sustentada que una enorme y floja.

Cada decisión de diseño deberá poder defenderse frente a los desafíos que describe este documento.