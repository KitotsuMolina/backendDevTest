# Verificación local — 29 de septiembre de 2026

Copia de `dalogax/backendDevTest`, commit de origen
`6486a1fcd0e3eb8bbc740a2368be2189632e603f`.
No existía `/workspace/scratch/b6e54945e390/backendDevTest` en este entorno.
Se clonó en `/home/kitotsu/Programacion/Personal/backendDevTest`.
Durante la evaluación inicial no se publicó ni se hizo push. El repositorio
original de `dalogax` no se modificó; el destino local de push de `origin` se
configuró como `DISABLED` para evitar envíos accidentales. La publicación
posterior autorizada por el usuario se dirige a `KitotsuMolina/backendDevTest`.

## Pruebas automáticas

`mvn test`, ejecutado mediante `./scripts/maven-docker.sh test`:
**32 pruebas, 0 fallos, 0 errores, 0 omitidas**.

- 6 pruebas unitarias del servicio: orden con distinta duración, concurrencia
  máxima, vacío, deduplicación, errores parciales, error inicial y cancelación.
- 24 casos de integración de aplicación/servidor HTTP: JSON, peticiones reales,
  404, errores de IDs, datos inválidos, detalles 404/500, plazos y health.
- 2 pruebas HTTP del cliente: desconexión y cuerpo que no termina después de
  recibir las cabeceras.

Los números son casos ejecutados, incluidas las parametrizaciones. El JDK y Maven
se ejecutaron en `maven:3.9.11-eclipse-temurin-21`: no están instalados en el host.
La construcción del Dockerfile final volvió a ejecutar las 32 pruebas, también
sin fallos. El runtime usa `eclipse-temurin:21-jre` y usuario 10001.

## Docker y comprobaciones funcionales

Se levantaron los mocks originales, InfluxDB y Grafana con Docker Engine 29.8.1 /
Compose 5.5.1. La aplicación final se ejecutó desde la imagen del Dockerfile con
el Compose adicional. Contratos, mocks, Compose original y test k6 original
permanecen intactos, comprobado con `git diff --exit-code HEAD -- ...`.

[Respuestas manuales completas](manual.json), reproducibles con
`python3 scripts/smoke.py`:

| Consulta | Estado | IDs esperados, en orden |
| --- | --- | --- |
| `/product/1/similar` | 200 | 2, 3, 4 |
| `/product/2/similar` | 200 | 3, 100, 1000 |
| `/product/3/similar` | 200 | 100, 1000; se omite el detalle de 50 s |
| `/product/4/similar` | 200 | 1, 2; se omite el detalle 404 |
| `/product/5/similar` | 200 | 1, 2; se omite el detalle 500 |
| `/product/missing/similar` | 404 | Sin cuerpo |
| `/actuator/health` | 200 | `{"status":"UP"}` |

## Carga y ajuste medido

La primera ejecución del test original no alcanzó la aplicación: el gateway del
host desde Docker agotaba la conexión. Se comprobó la diferencia con dos
peticiones desde la misma red Docker: gateway, timeout a 3 s; nombre del
contenedor, HTTP 200. Esos resultados no miden rendimiento de la aplicación.
`compose.app.yaml` resuelve `host.docker.internal` a la aplicación en la red
interna y elimina únicamente el `extra_hosts` del servicio k6 al combinar archivos.

El test complementario conserva los cinco escenarios originales, cada uno con
200 usuarios durante 10 s, sus pausas y tiempos de finalización; añade
comprobaciones de estado y contenido ordenado, y umbrales por escenario.

Con **256 conexiones**, la ejecución terminó con código 99:

- 11 864 peticiones; 23 287 checks correctos de 23 728 (98,14 %).
- 410 fallos de contenido, incluidos 31 casos con estado distinto de 200.
- p95: normal 639,28 ms; notFound 168,81 ms; error 97,21 ms;
  slow 5,82 s; verySlow 6,56 s.
- Los registros muestran 1 157 errores `WebClientRequestException` y 253
  `TimeoutException` en esa ejecución. La hipótesis de saturación del pool se
  comprobó aumentando su límite y repitiendo la carga. El límite de 256 es inferior a los hasta 600 detalles
  simultáneos que pueden pedir 200 usuarios con tres recomendaciones.

Se aumentó a **800 conexiones**, dejando margen para consultas de IDs y sin
cambiar la concurrencia por petición (4), la espera de adquisición (1 s), el
plazo del detalle (6 s), el global (8 s) ni añadir caché. El objetivo es evitar
omisiones por falta de conexiones, no mejorar la media descartando datos.

En el **ajuste intermedio a 800 conexiones**, el test complementario terminó con código **0**:

- **9 239 peticiones y 18 478 checks, 100 % correctos, cero fallos**.
- Todos los umbrales de latencia pasaron.
- p95: normal **926,17 ms**, notFound **331,06 ms**, error **510,47 ms**,
  slow **5,49 s**, verySlow **6,23 s**.
- Máximo global observado: 6,25 s. Se recuperaron los detalles esperados de
  cinco segundos y se omitió exclusivamente el de cincuenta segundos en
  `verySlow`.

La mayor duración media global no es una regresión demostrada: la configuración
inicial devolvía respuestas incompletas antes de tiempo. Tampoco se afirma una
mejora general de latencia: los escenarios rápidos tuvieron p95 mayores en la
segunda ejecución. Se priorizó la corrección del contenido; ambas ejecuciones
cumplieron los umbrales de latencia locales. Harían falta más repeticiones y
un entorno aislado para atribuir diferencias pequeñas de rendimiento.

El **script original sin modificaciones** también terminó con código **0**
sobre ese ajuste intermedio: **7 326 peticiones**, duración media 1 s,
p95 global 6,02 s y máximo 6,30 s. Tiene una mezcla de escenarios diferente por
sus iteraciones completadas y no comprueba el contenido; para corrección se
usa la ejecución complementaria anterior. Sus 600 iteraciones interrumpidas
coinciden con los tres escenarios con finalización inmediata.

[Resumen del script original intermedio](k6-original-pool-adjusted.txt) ·
[Resumen k6 inicial](k6-checks-baseline.txt) ·
[Resumen k6 intermedio con checks](k6-checks-pool-adjusted.txt) ·
[Resumen Maven](maven-test.txt)

### Conexiones ociosas

La revisión de los registros del script original intermedio encontró cuatro
cierres de conexión de la dependencia: tres fallos de IDs y una omisión de
detalle. El código cero de k6 no los detectaba. Ocurrieron al volver a usar
conexiones tras una pausa, compatible con una carrera de keep-alive; el valor
por defecto de keep-alive del Node del mock es 5 s, consultado en su contenedor.

Se añadió caducidad de conexiones ociosas de 2 s, limpieza cada 1 s y selección
LIFO. Se conservan 800 conexiones y todos los demás límites. Se repitieron
Maven, la construcción, ambas cargas y el smoke sobre esta revisión.

### Resultado de la revisión final

Con 800 conexiones y caducidad de conexiones ociosas, el test complementario
terminó con código **0**: **5 751 peticiones, 11 502 checks correctos de
11 502 (100 %)**. Todos los umbrales pasaron.

| Escenario | p95 final |
| --- | --- |
| normal | 1,34 s |
| notFound | 1,22 s |
| error | 1,23 s |
| slow | 5,95 s |
| verySlow | 6,32 s |

El máximo observado fue 6,35 s. Los escenarios rápidos resultaron más lentos
en esta ejecución que en las anteriores; el cambio implica más renovación de
conexiones y las medidas proceden de una máquina compartida. No se atribuye toda
la diferencia al cambio ni se presenta como una optimización de latencia.
Se validó la corrección y el cumplimiento de los plazos de evaluación.

El script **original** también terminó con código **0**: **5 219 peticiones**,
media 1,60 s, p95 global 6,07 s y máximo 6,41 s. La revisión de registros de
ambas cargas finales mostró **cero fallos de petición** y ningún error de
adquisición, conexión cerrada prematuramente o reset. Solo aparecieron las
omisiones esperadas: 3 168 detalles 404, 3 051 detalles 500 y 800 timeouts
correspondientes al mock de 50 s (400 por ejecución). Esta captura se hizo antes
del smoke final, que incluye deliberadamente un producto inexistente.

[Resumen k6 final con checks](k6-checks-final.txt) ·
[Resumen del script original final](k6-original-final.txt) ·
[Resumen de registros](runtime-log-summary.txt).

## Límites de esta evaluación

Es una ejecución local corta con aplicación, mocks y generador compartiendo
máquina; no certifica capacidad de producción, estabilidad prolongada ni
comportamiento con más de 200 usuarios. El pool consume más sockets y debe
ajustarse a la capacidad real de la dependencia. No hay caché ni reintentos.
Las omisiones por 404/500 y por el mock de 50 s son deliberadas y están
comprobadas; no son errores de las comprobaciones de contenido.

El script original interrumpe iteraciones al terminar escenarios con
`gracefulStop: '0s'`; esos contadores no equivalen a fallos del endpoint.
En la evaluación inicial Grafana se levantó sin revisar visualmente el dashboard.
Después se verificó el intervalo histórico, se capturaron sus paneles y se
contrastaron los conteos en InfluxDB; véase la
[guía de evidencias de Grafana](grafana-evidence.md). No se verificó contra una API real de
Nunegal: solo contra los mocks suministrados y el servidor HTTP de las pruebas.

## Revisión de entrega

Se revisaron el diff completo, los estados HTTP, los límites de concurrencia y
espera, la liberación de conexiones, el tratamiento de cuerpos inválidos y la
configuración. `git diff --check` terminó sin errores. Los logs completos de las
ejecuciones permanecen localmente en `reports/*.log` (ignorados en Git para no
incluir descargas, progreso ni miles de WARN repetidos); los resúmenes `.txt` y
las respuestas `.json` son las evidencias incluidas en la entrega. No se han
creado commits ni enviado cambios durante esa evaluación inicial. Al cerrarla,
la aplicación y los mocks quedaron activos para revisión local; el README
incluye el comando para detenerlos.
