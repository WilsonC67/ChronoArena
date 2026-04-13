=== ChronoArena ===

A 4-player LAN arena game. The server renders the game world and streams JPEG
frames to all connected clients over TCP. Players move with WASD or arrow keys
and compete to capture zones, collect power-ups, and freeze opponents.

--------------------------------------------------------------------------------
REQUIREMENTS
--------------------------------------------------------------------------------
  Java 17 or later on every machine (server and all clients).
  All machines must be on the same LAN, or use localhost for local testing.

  Download Java 17+: https://adoptium.net

--------------------------------------------------------------------------------
QUICK START — Pre-built JARs  (recommended)
--------------------------------------------------------------------------------
  The release includes two JARs:

    ChronoArena-server.jar   — run on one machine to host the game
    ChronoArena-client.jar   — run on each player's machine (up to 4 players)

  1. Start the server (one machine):

       java -jar ChronoArena-server.jar

     The server prints its status and opens the admin monitor window.
     Find your LAN IP with:
       Windows:   ipconfig
       Mac/Linux: ifconfig  or  ip a

  2. Start a client (each player machine):

       java -jar ChronoArena-client.jar <serverIp> <playerId>

     Examples:

       java -jar ChronoArena-client.jar 192.168.1.42 1
       java -jar ChronoArena-client.jar 192.168.1.42 2
       java -jar ChronoArena-client.jar localhost 1       <- local testing

     - serverIp  : LAN IP of the machine running the server JAR
     - playerId  : unique number 1-4, one per player

     If either argument is omitted a dialog box will prompt for it.

     You can also pass arguments as system properties:

       java -Dserver.ip=192.168.1.42 -Dplayer.id=3 -jar ChronoArena-client.jar

  3. Once players are connected, use the in-game lobby to ready up and vote to
     start. The round begins when all connected players have voted.

--------------------------------------------------------------------------------
CONFIGURATION  (config.properties)
--------------------------------------------------------------------------------
  Both JARs read config.properties from the working directory at startup.
  A default config.properties is bundled inside each JAR, so no editing is
  required for a standard setup. To override any setting, place a
  config.properties file in the same folder as the JAR — it will take
  precedence over the bundled defaults.

  Available settings:

    tcp.port            = 1234    # Frame streaming + JOIN handshake
    playerMonitor.port  = 6001    # UDP heartbeat receiver
    playerListener.port = 6002    # UDP game-action receiver
    tile.size           = 50      # Pixels per grid tile
    column.num          = 15      # Arena width in tiles
    row.num             = 12      # Arena height in tiles

  server.ip is used only as a fallback default on the client side.
  Client machines do NOT need to set it — pass the server IP on the
  command line instead (see above).

  Ports must be identical on every machine and must be open/unblocked
  by any local firewall:
    TCP  1234   (frame stream)
    UDP  6001   (heartbeats)
    UDP  6002   (game actions)

--------------------------------------------------------------------------------
BUILD FROM SOURCE — Mac
--------------------------------------------------------------------------------
  1. Install the JDK (if you don't have it already).

     With Homebrew (recommended):

       brew install --cask temurin

     Or download the macOS installer directly from https://adoptium.net and
     run it. Once installed, verify in a new Terminal window:

       java -version
       javac -version

     Both should report version 17 or later.

  2. Place all .java source files and config.properties in the same directory,
     then navigate there in Terminal:

       cd /path/to/chronoarena/src

  3. Compile all source files at once:

       javac -cp . *.java

     No output means success. Any errors will be printed with file and line.

  4. Run the server:

       java -cp . GameServer

  5. Run a client (open a new Terminal tab or window):

       java -cp . ChronoArenaClient 192.168.1.42 1

  6. (Optional) Repackage into JARs after making changes:

     First extract the existing manifests from the original JARs:

       jar xf ChronoArena-server.jar META-INF/MANIFEST.MF
       mv META-INF/MANIFEST.MF META-INF/MANIFEST-server.MF

       jar xf ChronoArena-client.jar META-INF/MANIFEST.MF
       mv META-INF/MANIFEST.MF META-INF/MANIFEST-client.MF

     Then repackage:

       jar cfm ChronoArena-server.jar META-INF/MANIFEST-server.MF *.class config.properties
       jar cfm ChronoArena-client.jar META-INF/MANIFEST-client.MF *.class config.properties

     The manifest files must contain the correct Main-Class entry:
       Main-Class: GameServer          (server)
       Main-Class: ChronoArenaClient   (client)

--------------------------------------------------------------------------------
BUILD FROM SOURCE — Windows
--------------------------------------------------------------------------------
  1. Download and install the JDK from https://adoptium.net.
     During install, make sure "Add to PATH" is checked.
     Verify in a new Command Prompt window:

       java -version
       javac -version

  2. Open Command Prompt and navigate to the source directory:

       cd C:\path\to\chronoarena\src

  3. Compile:

       javac -cp . *.java

  4. Run the server:

       java -cp . GameServer

  5. Run a client (new Command Prompt window):

       java -cp . ChronoArenaClient 192.168.1.42 1

--------------------------------------------------------------------------------
HOW IT WORKS
--------------------------------------------------------------------------------
  1. Each ChronoArenaClient connects to GameServer via TCP and sends
     "JOIN <playerId>". The server replies "WELCOME <id>", or
     "REJECT Server full" if 4 players are already connected. The cap is
     enforced atomically so two simultaneous connections can never both sneak
     past the limit.

  2. The server renders the full arena — grid, capture zones, players, items,
     beams — at 20 fps, and broadcasts it to every connected client over TCP.

  3. WASD / Arrow keys send UDP packets to the server, which updates the
     player's position on the grid and includes the change in the next frame.

  4. Zones are captured by standing inside them uncontested. A zone fully
     captures after 3 seconds and awards 5 points per second to its owner.
     Zones are randomised at the start of each round and shuffle periodically
     during play.

  5. Items spawn randomly on the grid:
       GUN         - fire a freeze beam at nearby opponents
       SHIELD      - absorbs one incoming freeze attack
       SPEED BOOST - move 2 tiles per keypress for 5 seconds
       ENERGY      - instant point bonus on pickup

  6. Hitting a frozen opponent with a GUN transfers half their points to the
     attacker. Frozen players also lose 1 HP per tick while frozen.

  7. When a player's HP reaches 0 they are eliminated temporarily and respawn
     after a short countdown.

  8. The round ends when the timer hits zero. Final scores are displayed and
     all players vote whether to play again.

--------------------------------------------------------------------------------
LOBBY & VOTING
--------------------------------------------------------------------------------
  After connecting, players land in the lobby.

  - READY UP    : toggle your ready status. The round duration can be adjusted
                  by any player before the vote closes.
  - VOTE START  : cast your vote to begin. The game starts as soon as every
                  connected player has voted.
  - After a round, players vote to play again. A unanimous "yes" restarts
    immediately; any "no" returns everyone to the lobby.

--------------------------------------------------------------------------------
SERVER MONITOR
--------------------------------------------------------------------------------
  A local admin GUI launches automatically on the server machine. It shows live
  player states, HP, scores, zone ownership, and a round timer.

  Each player slot has a kill-switch button to force-disconnect a misbehaving
  player mid-game (a confirmation dialog appears before anything is removed).

  Closing the monitor window will prompt for confirmation before shutting the
  server down. Clicking "No" keeps the server running with the window closed.

--------------------------------------------------------------------------------
TROUBLESHOOTING
--------------------------------------------------------------------------------
  Client cannot connect
    - Confirm the server is running and you have the correct LAN IP.
    - Check that TCP 1234 and UDP 6001/6002 are not blocked by a firewall.
    - Mac: System Settings -> Network -> Firewall -> ensure incoming connections
      for java are allowed, or temporarily disable the firewall for testing.
    - Windows: Windows Defender Firewall -> Allow an app -> add java.exe,
      or run the server terminal as Administrator.

  "REJECT Server full"
    - Four players are already connected. A fifth client cannot join.

  "REJECT Missing JOIN"
    - The client sent an unexpected message during the handshake. Make sure
      you are running a matching version of ChronoArena-client.jar.

  Choppy / blank frames
    - Ensure all machines are on the same network (avoid mixing WiFi and
      Ethernet subnets that isolate devices from each other).
    - Reduce tile.size or column.num/row.num in config.properties to lower
      the per-frame payload size.

  Player position drifts or lags
    - UDP packets can be lost on a congested network. The server's jitter
      buffer tolerates gaps of up to 20 ticks before advancing. Reduce
      background network load if movement feels unresponsive.