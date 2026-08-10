# Plan de transformación del cliente desktop

## Objetivo

Transformar el cliente desktop libGDX existente para que conserve el modo local de uno o dos jugadores y, además, permita conectarse a partidas online de hasta 16 naves en el mismo escenario.

La transformación será incremental. El modo local debe seguir siendo ejecutable sin servidor, sin conexión a Internet y sin modificar su comportamiento actual más de lo necesario.

## Estado de partida previsto

El cliente tendrá tres formas de iniciar el juego:

| Tecla | Acción |
|---|---|
| `1` | Iniciar partida local para un jugador |
| `2` | Iniciar partida local para dos jugadores |
| `J` | Entrar en una partida online existente |
| `C` | Crear una partida online y actuar como anfitrión |
| `ESC` | Salir o volver a la pantalla anterior |

La pantalla de presentación seguirá siendo el punto de entrada común. Las opciones online deberán aparecer en el mensaje de presentación sin eliminar ni ocultar las opciones locales.

La opción `C` no debe significar necesariamente que el cliente contenga una simulación especial. En la primera versión, puede iniciar un servidor local o solicitar al usuario la dirección/puerto del servidor anfitrión y después conectar el cliente. La forma exacta dependerá de cómo se implemente el servidor.

## Principios de compatibilidad

- Mantener `gradle build` funcional.
- Mantener `gradle :desktop:run` funcional.
- No exigir un servidor para las opciones `1` y `2`.
- No introducir dependencias de red en el arranque del modo local si pueden evitarse.
- Conservar el módulo `core` como lugar de la lógica compartida por desktop y futuras plataformas.
- Mantener `DesktopLauncher` como punto de entrada del cliente.
- Evitar que el renderizado dependa directamente del transporte de red.
- Permitir abandonar una partida online y volver a la presentación sin reiniciar el proceso.
- Mantener el formato y tamaño actuales del mundo para no romper las reglas visuales existentes.
- No sustituir la simulación local hasta que la versión online pueda probarse de forma independiente.

## Arquitectura del cliente

```text
PresentationScreen
  ├── modo local de 1 jugador
  ├── modo local de 2 jugadores
  ├── pantalla/formulario de JOIN
  └── pantalla/formulario de CREATE

GameScreen local
  ├── captura teclado local
  ├── simulación local existente
  └── renderizado

OnlineGameScreen
  ├── captura de entradas del jugador local
  ├── NetworkClient
  ├── recepción de snapshots
  ├── interpolación visual
  ├── predicción opcional posterior
  └── renderizado de hasta 16 jugadores
```

En una etapa posterior, `GameScreen` local y `OnlineGameScreen` podrán compartir un renderizador común, pero no es necesario forzar esa unificación desde el primer cambio.

## Fase 1: documentar y proteger el comportamiento actual

Antes de modificar el cliente:

- Ejecutar el modo de un jugador.
- Ejecutar el modo de dos jugadores.
- Verificar controles, vidas, disparos, escudo, hipersalto, asteroides, estrella y pantalla de fin.
- Ejecutar `gradle build`.
- Registrar cualquier comportamiento actual que deba conservarse.
- Añadir o conservar pruebas de las reglas que ya estén cubiertas.

Esta fase establece una referencia para detectar regresiones.

## Fase 2: ampliar la pantalla de presentación

Modificar la presentación para mostrar claramente cuatro opciones:

```text
Press 1 for local one-player
Press 2 for local two-player
Press J to JOIN an online game
Press C to CREATE an online game
Press ESC to exit
```

La implementación debe seguir usando el recurso de presentación existente cuando sea apropiado, o ampliar su formato de manera compatible. No se deben codificar textos duplicados en varios lugares sin necesidad.

En `PresentationScreen`:

- Conservar `NUM_1` para `startOnePlayer()`.
- Conservar `NUM_2` para `startTwoPlayers()`.
- Añadir `Input.Keys.J` para abrir la pantalla de unión.
- Añadir `Input.Keys.C` para abrir la pantalla de creación.
- Mantener `ESC` para salir si esa función ya existe.
- No iniciar conexiones de red desde `render()` directamente.
- Delegar cada opción en una transición de pantalla o controlador claramente identificado.

## Fase 3: separar la intención de inicio

Crear una representación explícita del modo de ejecución, por ejemplo:

- `LOCAL_ONE_PLAYER`.
- `LOCAL_TWO_PLAYERS`.
- `ONLINE_CLIENT`.
- `ONLINE_HOST`, si el anfitrión se ejecuta dentro de la misma aplicación.

El código actual utiliza `GameMode.ONE_PLAYER` y `GameMode.TWO_PLAYERS`. Es preferible conservar esos valores para el modo local y añadir una abstracción superior en vez de cambiar nombres públicos sin necesidad.

La clase `MarcianosGame` deberá centralizar las transiciones:

- `startOnePlayer()`.
- `startTwoPlayers()`.
- `showJoinScreen()`.
- `showCreateScreen()`.
- `startOnlineGame(...)`.
- `returnToPresentation()`.

Cada cambio de pantalla deberá liberar correctamente la pantalla anterior.

## Fase 4: pantallas de JOIN y CREATE

### JOIN

La pantalla de unión deberá permitir introducir o seleccionar:

- Dirección del servidor.
- Puerto.
- Código o nombre de sala, si el protocolo lo requiere.
- Nombre visible del jugador, opcional.
- Confirmar conexión.
- Cancelar y volver a la presentación.

Valores iniciales recomendados para desarrollo:

- Dirección: `localhost`.
- Puerto: el puerto documentado por el servidor.

No se deben guardar credenciales ni datos sensibles en esta primera versión.

Estados visuales mínimos:

- Conectando.
- Esperando respuesta.
- Unido a la sala.
- Partida iniciada.
- Error de conexión.
- Servidor lleno.
- Sala inexistente.
- Conexión perdida.

### CREATE

La primera implementación puede ofrecer dos alternativas configurables:

1. Conectar a un servidor previamente iniciado indicando dirección y puerto.
2. Lanzar un servidor local como proceso anfitrión y conectar automáticamente el cliente.

La segunda opción solo se implementará cuando exista un `server.jar` estable y una forma segura de localizarlo. No debe bloquear la creación de partidas con un servidor externo.

La pantalla deberá mostrar el puerto y, si procede, la información que el anfitrión debe compartir con los demás jugadores.

## Fase 5: extraer comandos de entrada

El `PlayerManager` actual consulta directamente `Gdx.input` para decidir si una nave rota, acelera, dispara, usa escudo o hace hipersalto. Para permitir la ejecución remota:

- Crear `InputCommand` con las acciones de un tick.
- Crear un adaptador de teclado que convierta `Gdx.input` en `InputCommand`.
- En modo local, entregar el comando directamente a la simulación.
- En modo online, enviar el comando al servidor.
- No hacer que el servidor dependa de `Gdx.input`.

La diferencia debe quedar conceptualmente así:

```text
Teclado -> InputCommand -> simulación local
Teclado -> InputCommand -> NetworkClient -> servidor
```

Las acciones mantenidas se enviarán como estados. Las acciones puntuales deberán llevar secuencia o transición para no repetirse al recibir reintentos.

## Fase 6: modelo visual para hasta 16 jugadores

El modo online no debe mantener únicamente `playerOne` y `playerTwo` como entidades de renderizado. Debe poder dibujar una colección de jugadores recibida del servidor.

Requisitos:

- Identificador estable por jugador.
- Color o distintivo visual único o suficientemente diferenciable.
- Nombre o número opcional en el HUD.
- Posición, velocidad, ángulo, escudo, vida y estado activo.
- Balas asociadas a su propietario.
- Nave local identificada para el HUD y efectos de control.
- Desaparición limpia de jugadores que abandonen la sala.

El máximo visible será 16, pero el código no debe duplicar manualmente 16 variables.

## Fase 7: cliente de red

Crear un componente `NetworkClient` responsable de:

- Abrir y cerrar la conexión.
- Enviar solicitudes de unión y comandos.
- Recibir mensajes en un hilo o mecanismo no bloqueante.
- Entregar snapshots al hilo de renderizado de forma segura.
- Detectar desconexiones.
- Informar de errores a la interfaz.
- No modificar directamente objetos gráficos desde el hilo de red.

La pantalla online deberá consumir una cola o el último snapshot válido. El hilo de renderizado no debe quedar bloqueado esperando datos de red.

## Fase 8: actualización visual e interpolación

El servidor será la autoridad del estado. El cliente deberá:

- Guardar al menos los dos últimos snapshots.
- Descartar snapshots antiguos.
- Interpolar posiciones y ángulos entre estados cuando sea posible.
- Renderizar el último estado disponible si falta un snapshot.
- Evitar que una breve ausencia de paquetes congele toda la ventana.
- Mostrar un estado de conexión o latencia opcional.

La predicción del jugador local y la reconciliación con el servidor se dejarán para una fase posterior, salvo que la latencia real lo haga necesaria.

## Fase 9: reutilización del renderizado

Separar, gradualmente, estas responsabilidades:

- `WorldRenderer`: dibuja naves, balas, asteroides, estrellas y explosiones.
- `HudRenderer`: dibuja vidas, puntuaciones, nombres y mensajes.
- `LocalGameController`: coordina la simulación local.
- `OnlineGameController`: coordina red y snapshots.

El renderizador no debe decidir colisiones, vidas ni puntuaciones. Los efectos temporales, como explosiones, podrán generarse a partir de eventos del servidor o de cambios observados entre snapshots.

## Fase 10: compatibilidad del modo local

Durante la transición se mantendrán dos rutas de ejecución:

### Modo local

- No requiere conexión.
- Conserva uno o dos jugadores.
- Usa controles locales.
- Puede continuar utilizando la simulación local mientras se extrae la lógica común.
- Se prueba con `1` y `2` desde la presentación.

### Modo online

- Requiere servidor.
- Usa un único jugador local por cliente.
- Recibe las demás naves desde el servidor.
- Puede tener hasta 16 jugadores en la misma partida.
- No debe confiar en estados calculados exclusivamente por el cliente.

Cuando la simulación compartida esté suficientemente probada, se podrá hacer que el modo local use la misma `GameSimulation` sin red. Esa unificación es deseable, pero no debe romper la ejecución local durante el proceso.

## Fase 11: pruebas

### Pruebas funcionales locales

- `1` inicia correctamente una partida de un jugador.
- `2` inicia correctamente una partida de dos jugadores.
- Los controles existentes siguen funcionando.
- `J` abre la pantalla de unión.
- `C` abre la pantalla de creación.
- Cancelar desde JOIN y CREATE devuelve a la presentación.
- `ESC` funciona en las pantallas correspondientes.

### Pruebas online

- Un cliente puede conectarse a `localhost`.
- Dos clientes pueden jugar en la misma partida.
- La partida admite progresivamente hasta 16 clientes.
- Todos observan las mismas colisiones y resultados.
- El jugador local se distingue de los demás.
- Un jugador que abandona desaparece correctamente.
- Un servidor lleno produce un mensaje claro.
- Un servidor apagado no bloquea el cliente.
- La ventana sigue respondiendo durante una pérdida de red.

### Pruebas de regresión

Después de cada fase:

- `gradle build`.
- Ejecución local de uno y dos jugadores.
- Prueba de cierre y reapertura de pantallas.
- Comprobación de que no quedan hilos de red activos al volver al menú.
- Comprobación de que no se filtran recursos libGDX.

## Orden recomendado de implementación

1. Crear pruebas o una referencia del comportamiento local actual.
2. Ampliar la presentación con `J` y `C`, inicialmente mostrando pantallas provisionales.
3. Introducir `InputCommand` y un adaptador de teclado.
4. Cambiar la lógica local para consumir comandos sin modificar la experiencia.
5. Extraer estados serializables.
6. Hacer que el renderizado admita una colección de jugadores.
7. Implementar `NetworkClient` contra un servidor de `localhost`.
8. Añadir `OnlineGameScreen`.
9. Añadir interpolación y estados de conexión.
10. Probar hasta 16 clientes.
11. Unificar progresivamente la simulación local y la del servidor.

## Criterios de aceptación

- Las teclas `1` y `2` mantienen el comportamiento local existente.
- Las teclas `J` y `C` ofrecen acceso claro a las partidas online.
- El cliente puede renderizar hasta 16 naves sin variables duplicadas por jugador.
- El cliente no determina como autoridad las posiciones, vidas o puntuaciones online.
- La interfaz sigue respondiendo durante operaciones de red.
- Las pantallas y recursos se liberan correctamente.
- La compilación y ejecución desktop existentes siguen funcionando.
- La ejecución local no necesita servidor ni Internet.
- El mismo cliente puede conectarse a un servidor en `localhost`, red local o Internet.

## Fuera del alcance inicial

- Cliente web.
- Aplicaciones móviles.
- Cuenta de usuario.
- Chat y voz.
- Matchmaking público.
- Predicción avanzada y reconciliación compleja.
- Soporte UDP antes de medir la necesidad.
- Reconexión transparente con conservación completa del estado.
- Servidor embebido obligatorio dentro del cliente.
