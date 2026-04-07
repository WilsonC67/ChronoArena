## CSC340 Project 2: ChronoArena
Team Members:
- Julian Colón 
- Adam Parks
- Wilson Chen
- Brody Chevrier

=== ChronoArena ===

A 4-player LAN arena game. The server renders the game world and streams it to
all connected clients. Players move using WASD or arrow keys.

--------------------------------------------------------------------------------
REQUIREMENTS
--------------------------------------------------------------------------------
  Java 17 or later on every machine.
  All machines must be on the same LAN (or use localhost for local testing).

--------------------------------------------------------------------------------
COMPILE  (run once, on every machine)
--------------------------------------------------------------------------------

  Go to the src folder and run:
    javac -cp . *.java

--------------------------------------------------------------------------------
CONFIGURATION  (config.properties)
--------------------------------------------------------------------------------
  Ports are shared across all machines and must match:

    tcp.port   = 1234    frame streaming + JOIN handshake
    udp.port   = 1235    movement / action packets
    tile.size  = 50      pixels per grid tile
    column.num = 15      arena width in tiles
    row.num    = 12      arena height in tiles

  server.ip in config.properties is only used as a fallback default.
  Client machines do NOT need to edit it — the server IP is passed on
  the command line instead (see below).

--------------------------------------------------------------------------------
RUN THE SERVER  (one machine on the LAN)
--------------------------------------------------------------------------------
  java -cp . GameServer

  The server listens on all network interfaces automatically.
  Find your LAN IP with:
    Windows:  ipconfig
    Mac/Linux: ifconfig  or  ip a

--------------------------------------------------------------------------------
RUN A CLIENT  (one per player machine, up to 4 players)
--------------------------------------------------------------------------------
  java -cp . ChronoArenaClient <serverIp> <playerId>

  Examples:
    java -cp . ChronoArenaClient 192.168.1.42 1
    java -cp . ChronoArenaClient 192.168.1.42 2
    java -cp . ChronoArenaClient localhost 1       ← local testing

  - serverIp  : LAN IP of the machine running GameServer
  - playerId  : unique number 1–4 for each player

  If either argument is omitted, a dialog box will prompt for it.

  You can also use system properties instead of arguments:
    java -Dserver.ip=192.168.1.42 -Dplayer.id=3 -cp . ChronoArenaClient

--------------------------------------------------------------------------------
HOW IT WORKS
--------------------------------------------------------------------------------
  1. ChronoArenaClient connects to GameServer via TCP and sends "JOIN <id>".
  2. Server registers the player in GameLogic and replies "WELCOME <id>".
     Up to 4 players can join; a 5th receives "REJECT Server full".
  3. Server renders the full arena (grid, zones, players, items) at 20 fps,
     JPEG-compresses each frame, and broadcasts it to all connected clients.
  4. WASD / Arrow keys send UDP packets directly to the server, which moves
     the player on the grid and includes them in the next rendered frame.
  5. Zones are captured by standing in them uncontested. Controlling a zone
     earns 5 points per second. Freeze opponents with a GUN pickup.

--------------------------------------------------------------------------------
TESTING UTILITIES
--------------------------------------------------------------------------------
  Simulate a UDP-only player (no display window):
    java -cp . GameServerTest player <id> [udpPort] [serverIp]

  Send malformed packets to stress-test server error handling:
    java -cp . GameServerTest malformed [serverIp]

  Run model unit tests (no server needed):
    java -cp . TestModels

  Run UDP queue unit tests (no server needed):
    java -cp . TestServerUDPQueue