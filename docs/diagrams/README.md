# Diagramas de la solución

Estos diagramas documentan el código implementado y sus valores por defecto.
No son una propuesta de funcionalidades pendientes. Empieza por arquitectura y
flujo, continúa con secuencia y singleton, y termina con despliegue y mejoras.

[**Abrir la galería navegable**](index.html).

| Diagrama | Qué explica | Imagen vectorial | Imagen PNG | Fuente editable |
| --- | --- | --- | --- | --- |
| 01 · Arquitectura | Capas y ubicación de las mejoras M1–M6 | [SVG](01-arquitectura.svg) | [PNG](01-arquitectura.png) | [PlantUML](01-arquitectura.puml) |
| 02 · Flujo | Entrada, IDs, detalles y decisiones 200/404/502/504 | [SVG](02-flujo-peticion.svg) | [PNG](02-flujo-peticion.png) | [PlantUML](02-flujo-peticion.puml) |
| 03 · Secuencia | Consultas simultáneas y conservación del orden | [SVG](03-secuencia.svg) | [PNG](03-secuencia.png) | [PlantUML](03-secuencia.puml) |
| 04 · Singleton | Instancias compartidas y estado separado por petición | [SVG](04-singleton-concurrencia.svg) | [PNG](04-singleton-concurrencia.png) | [PlantUML](04-singleton-concurrencia.puml) |
| 05 · Despliegue | Docker, mocks, pruebas HTTP, k6 y métricas | [SVG](05-despliegue-pruebas.svg) | [PNG](05-despliegue-pruebas.png) | [PlantUML](05-despliegue-pruebas.puml) |
| 06 · Mejoras | Problemas detectados, cambios y resultados medidos | [SVG](06-mejoras-medidas.svg) | [PNG](06-mejoras-medidas.png) | [PlantUML](06-mejoras-medidas.puml) |

## Cómo interpretarlos

- **M1:** separación de responsabilidades e inyección por constructor.
- **M2:** hasta cuatro consultas de detalle activas por petición, sin un hilo
  bloqueado dedicado a cada espera.
- **M3:** orden de similitud, eliminación de duplicados y omisión de detalles
  fallidos. El array puede ser parcial; no incluye metadatos de las omisiones.
- **M4:** dos segundos para IDs, seis por detalle y ocho para toda la operación.
  El plazo global puede cancelar el flujo en cualquier etapa; no es una espera
  adicional después de las consultas. La espera de conexión forma parte del
  plazo de llamada. Un fallo de adquisición de pool se clasifica como fallo
  externo (502 para IDs), salvo que venza antes el plazo de llamada/global.
- **M5:** endpoint de salud independiente de la API externa y registros de
  diagnóstico. Los mensajes de negocio se registran en el servicio; no se
  añaden campos de diagnóstico al JSON de respuesta.
- **M6:** pool compartido con 800 conexiones y cola limitada a 1024 adquisiciones;
  espera de adquisición de un segundo. Las conexiones ociosas caducan tras dos
  segundos, se limpian cada segundo y se seleccionan con LIFO. La caducidad por
  inactividad no corta una petición activa de cinco segundos.

El singleton lo gestiona Spring por bean y contexto. El servicio no guarda en
atributos la lista de una petición: cada suscripción reactiva tiene sus propios
datos. `flatMapSequential` puede recibir resultados fuera de orden y retenerlos
hasta emitirlos en el orden original. El diagrama 03 es una secuencia pedagógica,
no una captura de tiempos: el orden de llegada 4 → 2 → 3 es ilustrativo.

El diagrama 05 representa la modalidad Docker. InfluxDB y Grafana forman parte
del banco de pruebas, no del almacenamiento de productos. Las pruebas de
integración usan su propio servidor HTTP, distinto de los mocks de Compose.
No hay base de datos de negocio ni caché. No se añadió una política propia de
reintentos, un circuito de fallos ni un sistema de mensajería.

## Correspondencia con el código

| Diagrama | Fuentes principales |
| --- | --- |
| 01 y 02 | [Controlador](../../src/main/java/com/nunegal/similar/SimilarProductsController.java), [servicio](../../src/main/java/com/nunegal/similar/SimilarProductsService.java), [cliente](../../src/main/java/com/nunegal/similar/ProductApiClient.java), [errores](../../src/main/java/com/nunegal/similar/ApiExceptionHandler.java) |
| 03 y 04 | [Servicio](../../src/main/java/com/nunegal/similar/SimilarProductsService.java), [pool y WebClient](../../src/main/java/com/nunegal/similar/HttpClientConfiguration.java), [valores configurables](../../src/main/resources/application.yaml) |
| 05 | [Dockerfile](../../Dockerfile), [Compose adicional](../../compose.app.yaml), [tests](../../src/test/java/com/nunegal/similar), [k6 complementario](../../scripts/verify.js) |
| 06 | [Informe de verificación y evidencias](../../reports/verification.md) |

## Regenerar las imágenes

Desde la raíz del proyecto, con Docker:

```sh
./scripts/render-diagrams.sh
```

Genera SVG y PNG con PlantUML 1.2026.8, usando una imagen oficial fijada por
SHA-256. La primera ejecución requiere descargar la imagen. El renderizado se
hace localmente con la red del contenedor deshabilitada: las fuentes no se
mandan a un servidor público. No instala Java ni Graphviz en el sistema.
`style.puml` contiene el estilo compartido; no es un diagrama independiente.

Se comprobaron los seis diagramas con el renderizador y se revisaron
visualmente sus exportaciones. Este cambio es documental: no modifica la
aplicación ni requiere repetir Maven o k6.
