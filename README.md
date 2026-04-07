## CSC340 Project 2: ChronoArena
Team Members:
- Julian Colón 
- Adam Parks
- Wilson Chen
- Brody Chevrier

=== ChronoArena ===

SETUP
-----
Edit config.properties and set server.ip to the server machine's LAN IP.
All machines must have the same config.properties.

  server.ip  = <IP of machine running GameServer>
  tcp.port   = 1234    (frame streaming + JOIN handshake)
  udp.port   = 1235    (movement/action packets)

RUN THE SERVER  (one machine)
------------------------------
  java -jar GameServer.jar

RUN EACH CLIENT  (one per player machine, up to 4)
---------------------------------------------------
  java -jar ChronoArenaClient.jar

A dialog will ask for your Player ID (1-4).
Each player on the LAN must choose a unique ID.

Or supply the ID via system property to skip the dialog:
  java -Dplayer.id=2 -jar ChronoArenaClient.jar

HOW IT WORKS
------------
1. ChronoArenaClient connects to GameServer via TCP and sends "JOIN <id>".
2. Server registers the player in GameLogic and replies "WELCOME <id>".
3. Server renders the arena (grid + zones + players + items) at 20 fps,
   JPEG-compresses each frame, and broadcasts it to all connected clients.
4. WASD/Arrow keys send UDP movement packets directly to the server.
5. Up to 4 players can join; the server enforces the cap.
