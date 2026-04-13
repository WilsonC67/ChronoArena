## CSC340 Project 2: ChronoArena
Team Members:
- Julian Colón 
- Adam Parks
- Wilson Chen
- Brody Chevrier

## Description

A 4-player LAN arena game. The server renders the game world and streams
frames to all connected clients over TCP. Players move with WASD or arrow keys
and compete to capture zones, collect power-ups, and best your opponents.

--------------------------------------------------------------------------------
REQUIREMENTS
--------------------------------------------------------------------------------
  Java 17 or later on every machine (server and all clients).
  All machines must be on the same LAN, or use localhost for local testing.

  Download Java 17+: https://adoptium.net

--------------------------------------------------------------------------------
QUICK START — Pre-built JARs  (recommended)
--------------------------------------------------------------------------------
  The latest release includes two JARs:

    server.jar   — run on one machine to host the game
    client.jar   — run on each player's machine (up to 4 players)

  1. Start the server (one machine):

       java -jar server.jar

     The server prints its status and begins listening for connections.
     
     Find your LAN IP with:

       Windows:   ipconfig

       Mac/Linux: ifconfig  or  ip a

  2. Start a client (each player machine):

       java -jar client.jar <serverIp> <playerId>

     Examples:

       java -jar client.jar 192.168.1.42 1
       java -jar client.jar 192.168.1.42 2
       java -jar client.jar localhost 1       ← local testing (same machine)

     - serverIp  : LAN IP of the machine running server.jar
     - playerId  : unique number 1–4, one per player

     If either argument is omitted a dialog box will prompt for it.

     You can also pass arguments as system properties:

       java -Dserver.ip=192.168.1.42 -Dplayer.id=3 -jar client.jar

  3. Once players are connected, use the in-game lobby to ready up and vote to
     start. The round begins when all connected players have voted.

--------------------------------------------------------------------------------
CONFIGURATION  (config.properties)
--------------------------------------------------------------------------------
  Both JARs read config.properties from the working directory at startup.
  A default config.properties is bundled inside each JAR, so no editing is
  required for a standard setup. To override settings, place a
  config.properties file in the same folder as the JAR.

  Available settings:

    tcp.port           = 1234    # Frame streaming + JOIN handshake
    playerMonitor.port = 6001    # UDP heartbeat receiver
    playerListener.port= 6002    # UDP game-action receiver
    tile.size          = 50      # Pixels per grid tile
    column.num         = 15      # Arena width in tiles
    row.num            = 12      # Arena height in tiles

  server.ip is used only as a fallback default on the client side.
  Client machines do NOT need to set it — pass the server IP on the
  command line instead (see above).

  Ports must be identical on every machine and must be open/unblocked
  by any local firewall:
    TCP  1234   (frame stream)
    UDP  6001   (heartbeats)
    UDP  6002   (game actions)

--------------------------------------------------------------------------------
BUILD FROM SOURCE
--------------------------------------------------------------------------------
  If you prefer to compile the source yourself rather than using the JARs:

  1. Place all .java files and config.properties in the same directory.

  2. Compile:

       javac -cp . *.java

  3. Run the server:

       java -cp . GameServer

  4. Run a client:

       java -cp . ChronoArenaClient <serverIp> <playerId>

       java -cp . ChronoArenaClient 192.168.1.42 1

--------------------------------------------------------------------------------
HOW IT WORKS
--------------------------------------------------------------------------------
  1. Each ChronoArenaClient connects to GameServer via TCP and sends
     "JOIN <playerId>". The server replies "WELCOME <id>" (or
     "REJECT Server full" if 4 players are already connected).

  2. The server renders the full arena — grid, capture zones, players, items,
     beams — at 20 fps, JPEG-compresses each frame, and broadcasts it to
     every connected client over TCP.

  3. WASD / Arrow keys send UDP packets to the server, which updates the
     player's position on the grid and includes the change in the next frame.

  4. Zones are captured by standing inside them uncontested. A zone fully
     captures after 3 seconds and awards 5 points per second to its owner.
     Zones are randomised at the start of each round and shuffle periodically
     during play.

  5. Items spawn randomly on the grid:
       GUN         — fire a freeze beam at nearby opponents
       SHIELD      — absorbs one incoming freeze attack
       SPEED BOOST — move 2 tiles per keypress for 5 seconds
       ENERGY      — instant point bonus on pickup

  6. Hitting a frozen opponent with a GUN transfers half their points to the
     attacker. Frozen players also lose 1 HP per tick.

  7. When a player's HP reaches 0 they are eliminated temporarily and respawn
     after a short countdown.

  8. The round ends when the timer hits zero. Final scores are displayed and
     all players vote whether to play again.

--------------------------------------------------------------------------------
LOBBY & VOTING
--------------------------------------------------------------------------------
  After connecting, players land in the lobby.

  - READY UP    : toggle your ready status. The round timer can be adjusted
                  by any player before the vote closes.
  - VOTE START  : cast your vote to begin. The game starts as soon as every
                  connected player has voted.
  - After a round, players vote to play again. A unanimous "yes" restarts
    immediately; any "no" returns everyone to the lobby.

--------------------------------------------------------------------------------
SERVER MONITOR
--------------------------------------------------------------------------------
  A local admin GUI (ServerMonitorPanel) launches automatically on the server
  machine. It shows live player states, zone ownership, scores, and includes
  a kill-switch to remove a misbehaving player mid-game.

--------------------------------------------------------------------------------
TESTING UTILITIES  (source build only)
--------------------------------------------------------------------------------
  Simulate a UDP-only player (no display window):
    java -cp . GameServerTest player <id> [udpPort] [serverIp]

  Send malformed packets to stress-test server error handling:
    java -cp . GameServerTest malformed [serverIp]

  Run model unit tests (no server needed):
    java -cp . TestModels

  Run UDP queue unit tests (no server needed):
    java -cp . TestServerUDPQueue

--------------------------------------------------------------------------------
TROUBLESHOOTING
--------------------------------------------------------------------------------
  Client cannot connect
    - Confirm the server is running and you have the correct LAN IP.
    - Check that TCP 1234 and UDP 6001/6002 are not blocked by a firewall.
    - On Windows: Windows Defender Firewall → Allow an app → add java.exe,
      or run the server in a terminal as Administrator.

  "REJECT Server full"
    - Four players are already connected. A fifth client cannot join.

  Choppy / blank frames
    - Ensure all machines are on the same network (not mixed WiFi/Ethernet
      that isolates devices).
    - Reduce tile.size or column.num/row.num in config.properties to lower
      the per-frame payload size.

  Player position drifts or lags
    - UDP packets can be lost on a congested network. The server's jitter
      buffer (ServerUDPQueue) tolerates gaps of up to 20 ticks before
      advancing. Reduce background network load if movement feels unresponsive.