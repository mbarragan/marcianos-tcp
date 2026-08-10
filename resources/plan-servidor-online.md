# Plan del servidor online

## Objetivo

Crear un servidor dedicado y autoritativo para partidas de **Cutre-Marcianos** de hasta 16 jugadores simultáneos en un mismo escenario, sin necesidad de mantener una infraestructura comercial obligatoria.

El servidor será una aplicación Java independiente, sin ventana, audio ni dependencias de renderizado. Una partida tendrá una única simulación compartida y hasta 16 conexiones de cliente.

## Decisiones iniciales

- Modelo **cliente-servidor autoritativo**.
- Una instancia de servidor por partida, no una instancia por jugador.
- El servidor será la única autoridad para posiciones, velocidades, disparos, colisiones, asteroides, vidas, puntuaciones y resultado.
- Los clientes enviarán acciones de entrada; nunca posiciones que deban aceptarse como válidas.
- Comenzar con TCP para reducir la complejidad del primer prototipo.
- Mantener el diseño preparado para sustituir posteriormente el transporte por UDP si las mediciones lo justifican.
- Frecuencia inicial de simulación: 30 ticks por segundo.
- Frecuencia inicial de snapshots: entre 10 y 20 por segundo, ajustable tras las pruebas.
- Sin cuentas de usuario ni base de datos en la primera versión.
- Salas privadas mediante código o dirección/puerto compartidos.

## Arquitectura prevista

```text
server.jar
  ├── transporte y conexiones
  ├── gestión de salas y jugadores
  ├── recepción y validación de comandos
  ├── simulación autoritativa
  ├── generación de snapshots
  └── gestión de desconexiones

cliente desktop
  ├── captura de entrada
  ├── envío de InputCommand
  ├── recepción de WorldSnapshot
  ├── interpolación visual
  └── renderizado libGDX
```

## Separación de módulos

### Modelo compartido

Crear un módulo o paquete compartido, reutilizable por servidor y cliente, con los tipos de protocolo y datos:

- `PlayerState`.
- `BulletState`.
- `AsteroidState`.
- `WorldState` o `WorldSnapshot`.
- `InputCommand`.
- `JoinRequest`, `JoinAccepted`, `JoinRejected`.
- `LeaveMessage`.
- `GameEvent`, si se necesitan efectos puntuales como explosiones.
- Versión del protocolo.

Los objetos de red deben contener datos simples y no depender de `Gdx.input`, `ShapeRenderer`, ventanas o recursos gráficos.

### Simulación

Extraer una `GameSimulation` independiente de la pantalla. Como mínimo deberá:

1. Mantener una colección de hasta 16 jugadores.
2. Aplicar los comandos de entrada del tick correspondiente.
3. Actualizar naves, balas, asteroides y estrella.
4. Resolver todas las colisiones en el servidor.
5. Aplicar vidas, escudos, hipersalto y puntuaciones.
6. Generar eventos de explosión y desaparición cuando proceda.
7. Producir snapshots serializables.

La simulación no debe leer directamente teclado, ratón ni estado local del cliente.

## Protocolo inicial

### Cliente a servidor

El cliente enviará mensajes pequeños y frecuentes:

- Identificador de sesión.
- Número de secuencia.
- Tick al que corresponde la orden.
- Rotar izquierda.
- Rotar derecha.
- Acelerar.
- Disparar.
- Escudo.
- Hipersalto.
- Solicitud de abandonar la partida.

Las acciones mantenidas podrán enviarse como un conjunto de estados booleanos. Las acciones puntuales, como el hipersalto, deberán identificarse por transición o secuencia para evitar ejecuciones duplicadas.

### Servidor a cliente

El servidor enviará:

- Aceptación o rechazo de conexión.
- Identificador del jugador asignado.
- Estado de la sala.
- Inicio de partida.
- `WorldSnapshot` con tick, jugadores, balas, asteroides, estrella y puntuaciones.
- Eventos visuales que no sea necesario mantener como parte del estado persistente.
- Avisos de desconexión.
- Resultado de la partida.
- Motivo de expulsión o rechazo, si corresponde.

Cada snapshot debe incluir un número de tick para permitir descartar estados antiguos y realizar interpolación.

## Gestión de la partida

Estados de una sala:

1. `LOBBY`: acepta jugadores y muestra la lista conectada.
2. `COUNTDOWN`: todos los clientes reciben la cuenta atrás.
3. `RUNNING`: la simulación avanza a ritmo fijo.
4. `FINISHED`: se conserva el resultado durante un tiempo breve.
5. `CLOSED`: se cierran las conexiones y se libera la sala.

La primera versión puede tener una sola sala por proceso. La gestión de múltiples salas se dejará preparada, pero no será necesaria para el primer objetivo.

## Desconexiones y reconexión

Primera versión:

- Un jugador desconectado queda inactivo.
- Su nave puede desaparecer o quedar fuera de control según una regla definida.
- La partida continúa para los demás.
- El servidor notifica la salida a todos los clientes.

Fase posterior:

- Permitir reconexión mediante un token temporal.
- Recuperar el mismo identificador de jugador.
- Reenviar un snapshot completo al reconectar.
- Definir un tiempo máximo de reconexión.

## Seguridad mínima

Aunque el juego no sea comercial, el servidor no debe confiar en los clientes:

- Validar el tamaño y tipo de cada mensaje.
- Limitar la frecuencia de comandos.
- Ignorar ticks demasiado antiguos o futuros.
- No aceptar posiciones, vidas ni puntuaciones desde el cliente.
- Limitar la sala a 16 jugadores.
- Cerrar conexiones que envíen datos inválidos repetidamente.
- Usar un código de sala o contraseña opcional.
- Registrar errores sin incluir información sensible.

## Despliegue sin coste obligatorio

El servidor deberá empaquetarse como un JAR ejecutable:

```text
java -jar server.jar --port 7777
```

Orden de opciones de despliegue:

1. `localhost` para pruebas.
2. Red local doméstica.
3. Un jugador como anfitrión, ejecutando servidor y cliente.
4. Ordenador doméstico, Raspberry Pi, NAS o equipo antiguo.
5. Máquina virtual o proveedor gratuito, si las condiciones del proveedor resultan adecuadas.

El diseño no dependerá de un proveedor gratuito concreto. Si se publica en Internet, habrá que configurar firewall, redirección de puerto o una alternativa de conectividad, además de proteger el proceso frente a mensajes inválidos.

## Plan de implementación

### Fase S1: modelo y simulación

- Extraer el estado del mundo de `GameScreen`.
- Sustituir la dependencia de dos jugadores fijos por una colección de jugadores.
- Eliminar la lectura de `Gdx.input` de la lógica de simulación.
- Añadir pruebas unitarias para movimiento, disparos, colisiones y vidas.

### Fase S2: límite de 16 jugadores en local

- Crear 16 jugadores artificiales o entradas simuladas.
- Verificar colisiones entre todos los jugadores y balas.
- Medir CPU y memoria.
- Comprobar que el resultado es reproducible con una semilla controlada cuando sea necesario.

### Fase S3: transporte en localhost

- Implementar conexión TCP.
- Implementar mensajes de unión, entrada, snapshot y salida.
- Ejecutar servidor y varios clientes en el mismo equipo.
- Añadir logs de ticks, latencia y tamaño de mensajes.

### Fase S4: red local

- Probar entre varios ordenadores.
- Medir latencia, pérdida percibida y frecuencia de snapshots.
- Añadir interpolación en el cliente.
- Probar desconexiones y reconexiones básicas.

### Fase S5: Internet

- Ejecutar un servidor fuera de la red local.
- Probar partidas con pocos jugadores.
- Añadir autenticación de sala y límites de seguridad.
- Documentar la ejecución para el anfitrión.

## Criterios de aceptación

- Una partida admite entre 1 y 16 jugadores.
- Todos los clientes ven el mismo resultado de las colisiones y puntuaciones.
- Ningún cliente puede cambiar directamente su posición, vidas o puntuación.
- Un cliente desconectado no detiene la partida de los demás.
- El servidor funciona sin interfaz gráfica.
- La simulación puede probarse sin abrir una ventana libGDX.
- El tráfico y consumo son razonables para una conexión doméstica.
- La versión local de uno y dos jugadores sigue funcionando.

## Fuera del alcance inicial

- Emparejamiento público.
- Cuentas y persistencia de usuarios.
- Clasificaciones online.
- Chat.
- Voz.
- Múltiples regiones.
- Alta disponibilidad.
- Anti-trampas avanzado.
- Migración automática de anfitrión.
- UDP antes de disponer de mediciones que demuestren su necesidad.
