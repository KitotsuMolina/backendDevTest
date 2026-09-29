# Backend dev technical test

## Empieza por la galería navegable

> **[Abrir la galería visual de la solución →](docs/diagrams/index.html)**
>
> Recorre la arquitectura, el flujo de una petición y las mejoras implementadas
> antes de entrar en el código. Cada diagrama se puede ampliar y descargar
> como SVG, PNG o fuente editable PlantUML.

**Recorrido recomendado:** arquitectura → flujo y errores → secuencia de
llamadas → singleton y concurrencia → despliegue y pruebas → mejoras medidas.

Para navegar por la galería, abre `docs/diagrams/index.html` en un navegador
tras descargar o clonar el repositorio. GitHub muestra el código del HTML;
para ver las imágenes directamente allí, utiliza la
[guía de diagramas con enlaces a SVG y PNG](docs/diagrams/README.md).

### Gráficas y resultados de las pruebas

| Qué revisar | Evidencia |
| --- | --- |
| Carga y tiempos de respuesta | [Captura real de Grafana](output/playwright/grafana-prueba-original-final.png): peticiones, estados HTTP y 200 usuarios virtuales. |
| Cómo repetir la demostración | [Guía de Grafana y tests](reports/grafana-evidence.md): comandos, intervalo histórico y explicación de los paneles. |
| Corrección y mejoras verificadas | [Informe de resultados](reports/verification.md): 32 pruebas sin fallos y 11 502 checks correctos en la última carga complementaria. |

## Enunciado original

We want to offer a new feature to our customers showing similar products to the one they are currently seeing. To do this we agreed with our front-end applications to create a new REST API operation that will provide them the product detail of the similar products for a given one. [Here](./similarProducts.yaml) is the contract we agreed.

We already have an endpoint that provides the product Ids similar for a given one. We also have another endpoint that returns the product detail by product Id. [Here](./existingApis.yaml) is the documentation of the existing APIs.

**Create a Spring boot application that exposes the agreed REST API on port 5000.**

![Diagram](./assets/diagram.jpg "Diagram")

Note that _Test_ and _Mocks_ components are given, you must only implement _yourApp_.

## Testing and Self-evaluation
You can run the same test we will put through your application. You just need to have docker installed.

First of all, you may need to enable file sharing for the `shared` folder on your docker dashboard -> settings -> resources -> file sharing.

Then you can start the mocks and other needed infrastructure with the following command.
```
docker-compose up -d simulado influxdb grafana
```
Check that mocks are working with a sample request to [http://localhost:3001/product/1/similarids](http://localhost:3001/product/1/similarids).

To execute the test run:
```
docker-compose run --rm k6 run scripts/test.js
```
Browse [http://localhost:3000/d/Le2Ku9NMk/k6-performance-test](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test) to view the results.

## Evaluation
The following topics will be considered:
- Code clarity and maintainability
- Performance
- Resilience

## Implementación

Java 21, Maven 3.9 y Spring Boot 3.5.6. `GET /product/{productId}/similar`
responde en el puerto **5000** con un array JSON de productos ordenado por
similitud. Se conservan el contrato y los mocks originales.

### Arranque y pruebas

Con Java 21 y Maven instalados:

```sh
mvn test
mvn package
docker compose -p nunegal up -d simulado influxdb grafana
java -jar target/similar-products-1.0.0.jar
```

O con Docker y Compose >= 2.24 como únicos requisitos, desde la raíz del repositorio:

```sh
./scripts/maven-docker.sh test
docker compose -p nunegal -f docker-compose.yaml -f compose.app.yaml up -d --build app influxdb grafana
# Esperar a que health responda antes de lanzar la carga.
curl --fail --retry 30 --retry-connrefused --retry-delay 1 --max-time 2 --retry-max-time 60 http://localhost:5000/actuator/health
curl -i http://localhost:5000/product/1/similar
curl -i http://localhost:5000/actuator/health
docker compose -p nunegal -f docker-compose.yaml -f compose.app.yaml run --rm k6 run scripts/test.js
```

En esta modalidad, `host.docker.internal` resuelve directamente a la aplicación
en la red Compose. Esto evita depender del acceso al gateway del host desde
Docker (bloqueado en algunos entornos Linux) y conserva intacto `shared/k6/test.js`.
Para una aplicación arrancada con Java en el host, se usa el Compose original
sin `compose.app.yaml`; debe ser accesible desde el gateway de Docker.

El Dockerfile compila y ejecuta los tests antes de construir la imagen de
runtime, que usa un usuario sin privilegios. La primera ejecución descarga
imágenes y dependencias. `scripts/maven-docker.sh` conserva la caché Maven en
`.m2/` dentro de la copia local y evita generar archivos propiedad de root.

Comprobación reproducible de los seis casos originales y salud (Python 3):

```sh
python3 scripts/smoke.py
```

Guarda las respuestas y tiempos en `reports/manual.json`. Espera los plazos
por defecto y permite elegir otra URL con `APP_BASE_URL`.

Prueba de carga adicional con comprobaciones de estado, contenido y orden:

```sh
mkdir -p reports
docker compose -p nunegal -f docker-compose.yaml -f compose.app.yaml run --rm -v "$PWD/scripts:/verification:ro" k6 run /verification/verify.js
```

Usa los mismos escenarios, 200 usuarios y pausas que el test original. Sus
umbrales son criterios locales de evaluación, no requisitos impuestos por el
contrato: p95 < 2 s para los casos rápidos y < 7,5 s para los lentos, con el
100 % de comprobaciones satisfechas. El script original no contiene checks
ni thresholds: terminar con código cero por sí solo no demuestra corrección.

Para detener exclusivamente los servicios de esta prueba:

```sh
docker compose -p nunegal -f docker-compose.yaml -f compose.app.yaml down
```

### Decisiones y límites

| Situación | Respuesta |
| --- | --- |
| IDs obtenidos, todos los detalles correctos | 200 con productos en orden |
| Lista de IDs vacía | 200 con `[]`, sin pedir el detalle del producto original |
| Consulta de IDs devuelve 404 | 404 sin cuerpo |
| Consulta de IDs devuelve otro error, cuerpo vacío o inválido, o falla la conexión | 502 sin cuerpo |
| Consulta de IDs supera su plazo | 504 sin cuerpo |
| Un detalle devuelve 404, 5xx, datos inválidos o supera su plazo | Se omite; 200 con los demás, manteniendo su orden |
| Fallan todos los detalles | 200 con `[]` |
| Se agota el plazo global de la operación | 504 sin cuerpo; se cancelan las llamadas pendientes |

El contrato solo define 200/404. 502/504 son extensiones explícitas para fallos
de infraestructura: devolver 404 ocultaría un fallo de la dependencia y devolver
200 con `[]` en la consulta de IDs fingiría que no existen recomendaciones.
El 404 de un detalle no significa que no exista el producto consultado. No se
hace una llamada adicional para comprobar su existencia: la consulta de IDs es
la fuente de esa decisión. Un 200 vacío no permite distinguir inexistencia de
ausencia de recomendaciones si el proveedor no hace esa distinción.

La política de resultados parciales favorece mostrar productos disponibles;
el array contratado no permite indicar al consumidor cuáles se omitieron.
Es una decisión de esta solución, no una política especificada por el enunciado.
Los registros permiten diagnosticar las omisiones. No se añaden campos al JSON.
Los IDs repetidos se consultan una vez, manteniendo la primera posición.
Se aceptan cadenas y enteros porque los mocks usan números; se rechazan otros
tipos. Se valida la presencia de los campos obligatorios y que el detalle
corresponda al ID solicitado; `price` se representa con `BigDecimal`.

El controlador solo expone el endpoint. `SimilarProductsService` decide sobre
orden, fallos parciales y plazo global; `ProductApiClient` gestiona HTTP,
deserialización, validación y plazos de las llamadas. WebFlux/WebClient evita
retener un hilo por cada espera. `flatMapSequential` inicia detalles en paralelo
con un límite por petición y conserva el orden, incluso si terminan al revés.
El pool compartido limita conexiones globales y su cola también está acotada.
Las conexiones ociosas caducan a los 2 s, con limpieza cada segundo y preferencia
por las usadas más recientemente (LIFO). Esto reduce carreras con el cierre
keep-alive del mock después de las pausas del test. No elimina la posibilidad
de que un servidor cierre una conexión activa; ese caso sigue la política de
fallos indicada arriba.
Los plazos de cada llamada incluyen adquisición de conexión y lectura completa
del cuerpo; el plazo global limita listas con muchas tandas de detalles. Al
cancelar se libera el trabajo del cliente, aunque el proveedor podría continuar
procesando una petición que ya recibió.

No se añaden reintentos ni caché: los primeros pueden amplificar fallos bajo
carga; la segunda podría mostrar precio/disponibilidad antiguos. Cada respuesta
consulta datos de la API externa. No hay TTL ni coherencia de caché que gestionar.
La respuesta tampoco es una instantánea transaccional de todos los productos.

### Configuración

Se puede usar variables de entorno o propiedades estándar de Spring Boot.
Para Docker Compose, declara las variables adicionales en `app.environment`
en `compose.app.yaml`; las variables del shell no se pasan automáticamente
al contenedor salvo las interpoladas explícitamente en ese archivo.
Los límites y duraciones deben ser positivos; una configuración inválida impide
el arranque.

| Variable | Valor por defecto | Propósito |
| --- | --- | --- |
| `PORT` | `5000` | Puerto HTTP |
| `PRODUCTS_BASE_URL` | `http://localhost:3001` | API externa; Compose usa `http://simulado:80` |
| `PRODUCTS_CONCURRENCY` | `4` | Detalles simultáneos por petición |
| `PRODUCTS_MAX_CONNECTIONS` | `800` | Conexiones compartidas con la API externa |
| `PRODUCTS_PENDING_CONNECTIONS` | `1024` | Máximo de solicitudes esperando una conexión |
| `PRODUCTS_CONNECT_TIMEOUT` | `1s` | Conexión TCP |
| `PRODUCTS_ACQUIRE_TIMEOUT` | `1s` | Espera de conexión en el pool |
| `PRODUCTS_IDS_TIMEOUT` | `2s` | Consulta de IDs completa |
| `PRODUCTS_DETAIL_TIMEOUT` | `6s` | Consulta de cada detalle completa |
| `PRODUCTS_REQUEST_TIMEOUT` | `8s` | Operación completa |

La carga reveló omisiones con 256 conexiones: 98,14 % de checks correctos.
La revisión final con 800 y caducidad de conexiones obtuvo 11 502 checks
correctos de 11 502, manteniendo los mismos plazos.
El pool permite atender hasta 600 detalles simultáneos de los 200 usuarios del
test, con margen para IDs; no pretende certificar una capacidad de producción.

El plazo de detalle de 6 s permite incluir el mock de 5 s y corta el de 50 s.
Reducirlo mejora latencia a costa de omitir más recomendaciones. Aumentarlo
requiere revisar también el plazo global y el dimensionado de conexiones.
El límite global de 8 s puede producir un 504 para listas mayores que las de los
mocks; no hay paginación en el contrato y no se inventa un truncamiento silencioso.
El cuerpo externo tiene un límite de 256 KiB para acotar memoria por llamada.

`GET /actuator/health` comprueba que la aplicación está viva; deliberadamente no
consulta la dependencia ni demuestra que esté disponible. Solo se expone health,
sin detalles internos. Los WARN identifican la fase, categoría de fallo y estado
HTTP/tipo de excepción, sin cuerpos, URLs completas ni IDs arbitrarios. No hay
trazas de pila para fallos esperados. Bajo fallos masivos estos registros pueden
ser numerosos; en producción convendría añadir métricas y muestreo.

### Alcance y evidencias

**Obligatorio:** endpoint y puerto, forma del JSON, consulta de APIs existentes,
orden, 200/404 y lista vacía.

**Mejoras:** separación de responsabilidades, concurrencia y esperas acotadas,
tratamiento de fallos parciales, validación de respuestas externas, pruebas
unitarias y de integración HTTP, salud, registros, configuración por entorno,
Docker y comprobaciones funcionales bajo carga.

Las pruebas unitarias usan tiempo virtual para verificar orden, concurrencia,
deduplicación y cancelación. Las de integración arrancan la aplicación en un
puerto aleatorio y un servidor HTTP simulado real; verifican llamadas, JSON,
estados, datos inválidos, fallos parciales y esperas máximas. No requieren Docker
si Java/Maven están instalados. Los resultados ejecutados se documentan en
[reports/verification.md](reports/verification.md).
