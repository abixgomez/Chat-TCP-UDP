package server;
import java.util.*;
import java.time.*;
import java.time.format.*;
import java.io.*;
import java.net.*;

/**
 * Servidor de chat que implementa comunicación mediante
 * los protocolos TCP y UDP.
 *
 * El servidor permite:
 * - Registro de usuarios.
 * - Envío de mensajes públicos.
 * - Mensajes privados.
 * - Listado de usuarios conectados.
 * - Expulsión de usuarios.
 * - Comunicación concurrente mediante hilos.
 *
 * El servidor soporta un máximo de 5 clientes conectados
 * simultáneamente.
 *
 * @author abigomez
 */
public class TCPServer { 
    
    private static final int maxClientes = 5;
    
    //TCP
    private static final int PORT = 7777; // Puerto TCP
    private ServerSocket serverSocket;
    private static Map<String,PrintWriter> clientes = 
    Collections.synchronizedMap(new HashMap<>());
    private static Map<String, ClientHandler> nombresTCP =
    Collections.synchronizedMap(new HashMap<>());
    
    //UDP
    private static Set<String> clientesUDP =
    Collections.synchronizedSet(new HashSet<>());
    private static Map<String,String> nombresUDP =
    Collections.synchronizedMap(new HashMap<>());
    

    /**
    * Constructor de la clase TCPServer.
    *
    * Inicializa una instancia del servidor.
    */
    public TCPServer() {}
 
    /**
     * Inicia el servidor utilizando el protocolo TCP.
     *
     * El método crea un ServerSocket que escucha conexiones
     * entrantes en el puerto definido. Por cada cliente
     * conectado se crea un hilo independiente para manejar
     * la comunicación concurrente.
     *
     * También valida el número máximo de clientes permitidos.
     */
    public void startTCP() {
        
        try {
             // ServerSocket espera conexiones entrantes
            serverSocket = new ServerSocket(PORT);
            
            System.out.println("=== SERVIDOR TCP INICIADO ===");
            System.out.println("Escuchando en puerto: " + PORT);
            System.out.println("Esperando clientes...\n");
            
            // Bucle infinito para aceptar conexiones
            while(true){
                Socket clientSocket = serverSocket.accept();
                synchronized (clientes){
                    if(clientes.size() >= maxClientes){
                        PrintWriter tempOut = new PrintWriter(clientSocket.getOutputStream(), true);
                        tempOut.println("Servidor lleno. Intenta más tarde.");
                        clientSocket.close();
                        
                        System.out.println("[INFO] Conexión rechazada (servidor lleno): "+
                                clientSocket.getInetAddress().getHostAddress());
                        continue;
                    }
                }
                
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                
                System.out.println("[INFO] Cliente conectado: " +
                        clientSocket.getInetAddress().getHostAddress() +
                        " Puerto: " + clientSocket.getPort());
                
                Thread clientThread = new Thread(new ClientHandler(clientSocket,out));
                clientThread.start();
           }
            
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        } finally {
            stop();
        }
    }
    
    /**
    * Cierra el ServerSocket y detiene el servidor TCP.
    *
    * Libera el puerto utilizado y finaliza la espera
    * de nuevas conexiones.
    */
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                
            }
        } catch (IOException e) {
            System.err.println("Error al cerrar servidor: " + e.getMessage());
        }
    }

    /**
     * Clase interna encargada de manejar la comunicación
     * individual de cada cliente conectado al servidor.
     *
     * Implementa Runnable para ejecutar cada cliente
     * en un hilo independiente.
     */
    private static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private BufferedReader in;
        private PrintWriter out;
        private String clientInfo;
        private String nombre;

        /**
         * Constructor de ClientHandler.
         *
         * @param socket Socket asociado al cliente conectado.
         * @param out Flujo de salida para enviar mensajes al cliente.
         */
        public ClientHandler(Socket socket, PrintWriter out) {
            this.clientSocket = socket;
            this.out = out;
            this.clientInfo = socket.getInetAddress().getHostAddress()+
            ":"+socket.getPort();
        }

        /**
         * Ejecuta la comunicación con el cliente.
         *
         * El método valida el nombre de usuario, procesa
         * mensajes y comandos enviados por el cliente,
         * administra desconexiones y realiza el cierre
         * correcto del socket.
         */
        @Override
        public void run() {
            try {
                // Flujo de entrada (Leer del cliente)
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                while (true) {
                    this.nombre = in.readLine();

                    if (nombre == null) return;

                    nombre = nombre.trim();

                    synchronized(clientes) {
                        if (!nombre.isEmpty() && nombre.matches("^[a-zA-Z0-9]{3,15}$") && !clientes.containsKey(nombre.toLowerCase())) {
                            clientes.put(nombre.toLowerCase(), out);
                            this.nombre = nombre;
                            nombresTCP.put(nombre.toLowerCase(), this);    
                            
                            System.out.println("El cliente '" +nombre + "' se unió al servidor.");                           
                            out.println("1");  
                            broadcastSistema(nombre + " se ha unido al chat.");  
                            break;
                        } else {
                            out.println("0"); // mensaje interno
                        }
                    }
                }

                String inputLine;
                
                // Leer línea por línea
                while ((inputLine = in.readLine()) != null) {
                    
                    inputLine = inputLine.trim();
                    
                    if(inputLine.equalsIgnoreCase("exit")) break;
                    
                    if(inputLine.equalsIgnoreCase("/usuarios")){
                        listarUsuarios();
                    }else if(inputLine.startsWith("/msg ")){
                        mensajePrivado(inputLine);  
                    }else if(inputLine.startsWith("/kick ")){
                        kick(inputLine);
                    }else{
                        mensaje(inputLine);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error de conexión con cliente: " + e.getMessage());
            } finally {
                if(nombre != null){
                    clientes.remove(nombre.toLowerCase());
                    nombresTCP.remove(nombre.toLowerCase());
                    broadcastSistema(nombre + " ha salido del chat.");
                }
                try {
                    if (clientSocket != null) {
                        clientSocket.close();
                    }

                    String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                    System.out.println(
                        "[" + timestamp + "] [INFO] Cliente desconectado: "
                        + nombre + " " + clientInfo
                    );
                } catch (IOException e) {
                    // Ignorar
                }
            }
        }
        
        /**
         * Envía un mensaje del sistema a todos los clientes
         * conectados al chat.
         *
         * @param msg Mensaje del sistema a enviar.
         */
        private void broadcastSistema(String msg){
            String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String mensajeFinal = "[" + timestamp + "] [SERVIDOR] " + msg;

            synchronized (clientes){
                for(PrintWriter pw : clientes.values()){
                    pw.println(mensajeFinal);
                }
            }
        }
        
        /**
         * Envía un mensaje público a todos los clientes
         * conectados.
         *
         * @param msg Mensaje enviado por el usuario.
         */
        private void mensaje(String msg){
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String mensajeFinal = "[" +timestamp + "] "+nombre + ": "+msg;
            
            synchronized (clientes){
                for(PrintWriter pw :clientes.values()){
                    pw.println(mensajeFinal);
                }
            }
        }
        
        /**
         * Envía un mensaje privado a un usuario específico.
         *
         * El comando debe tener el formato:
         * /msg usuario mensaje
         *
         * @param inputLine Línea completa ingresada por el cliente.
         */
        private void mensajePrivado(String inputLine){
            String[] partes = inputLine.split("\\s+", 3);
            
            if(partes.length < 3){
                out.println("Uso: '/msg' usuario mensaje");
                return;
            }
            
            String destino = partes[1];
            String mensaje = partes[2];

            synchronized (clientes){
                PrintWriter priv = clientes.get(destino.toLowerCase());
            
                if(priv != null){
            
                    priv.println("[PRIVADO] " +nombre +": " +mensaje);
                    out.println("[A " + destino + "] " +mensaje);
                } else {
                    out.println("Usuario no encontrado");
                }
            }
        }
        
        /**
         * Expulsa a un usuario del servidor.
         *
         * El comando debe tener el formato:
         * /kick usuario
         *
         * @param inputLine Línea ingresada por el cliente.
         */
        private void kick(String inputLine){

            String[] partes = inputLine.split("\\s+", 2);

            if(partes.length < 2){
                out.println("Uso: /kick usuario");
                return;
            }

            String usuario = partes[1].toLowerCase();

            synchronized(nombresTCP){
                ClientHandler objetivo = nombresTCP.get(usuario);
                if(objetivo != null){
                    objetivo.out.println("[SERVIDOR] Has sido expulsado del chat.");

                    try{
                        objetivo.clientSocket.close();
                    }catch(IOException e){
                        System.err.println("Error expulsando usuario");
                    }
                    
                    broadcastSistema(usuario + " fue expulsado del servidor.");
                } else {
                    out.println("Usuario no encontrado.");
                }
            }
        }
        
        /**
         * Envía al cliente la lista de usuarios
         * actualmente conectados al servidor.
         */
        private void listarUsuarios() {
            StringBuilder lista = new StringBuilder("Usuarios conectados: ");

            synchronized (clientes) {
                for (String user : clientes.keySet()) {
                    lista.append(user).append(", ");
                }
            }
            if (lista.toString().endsWith(", ")) {
                lista.setLength(lista.length() - 2); // quitar última coma
            }
            out.println(lista.toString());
        }
    }
    
    /**
     * Inicia el servidor utilizando el protocolo UDP.
     *
     * El servidor recibe y envía datagramas para
     * permitir la comunicación entre múltiples clientes.
     *
     * También valida nombres de usuario y el límite
     * máximo de conexiones.
     */
    public void startUDP() {
        System.out.println("=== SERVIDOR UDP INICIADO ===");

        try {
            DatagramSocket socket = new DatagramSocket(PORT);
            byte[] buffer = new byte[1024];


            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String mensaje = new String(packet.getData(), 0, packet.getLength()).trim();

                InetAddress ip = packet.getAddress();
                int port = packet.getPort();

                String clienteId = ip.getHostAddress() + ":" + port;
                
                if (!nombresUDP.containsKey(clienteId)) {

                    if (clientesUDP.size() >= 5) {

                        String resp = "servidor_lleno";
                        byte[] data = resp.getBytes();

                        DatagramPacket full = new DatagramPacket(
                            data,
                            data.length,
                            ip,
                            port
                        );

                        socket.send(full);
                        continue;
                    }
                    if (nombresUDP.containsValue(mensaje)) {

                        String resp = "usuario_existente";

                        byte[] data = resp.getBytes();

                        DatagramPacket duplicate = new DatagramPacket(
                            data,
                            data.length,
                            ip,
                            port
                        );

                        socket.send(duplicate);

                        continue;
                    }
                    
                    if (!mensaje.matches("^[a-zA-Z0-9]{3,15}$")) {

                        String resp = "usuario_invalido";

                        byte[] data = resp.getBytes();

                        DatagramPacket invalid = new DatagramPacket(
                            data,
                            data.length,
                            ip,
                            port
                        );

                        socket.send(invalid);

                        continue;
                    }
                    

                    clientesUDP.add(clienteId);
                    nombresUDP.put(clienteId, mensaje);

                    System.out.println("Usuario registrado: " + mensaje);

                    broadcastUDP(
                        socket,
                        clientesUDP,
                        nombresUDP,
                        "[SERVIDOR] " + mensaje + " se unió"
                    );

                    continue;
                }
                
                if (mensaje.equalsIgnoreCase("exit")) {

                    String nombre = nombresUDP.get(clienteId);

                    nombresUDP.remove(clienteId);
                    clientesUDP.remove(clienteId);

                    broadcastUDP(
                        socket,
                        clientesUDP,
                        nombresUDP,
                        "[SERVIDOR] " + nombre + " salió"
                    );

                    System.out.println(nombre + " salió del chat UDP.");
                    continue;
                }

                String nombre = nombresUDP.get(clienteId);
                String salida = nombre + ": " + mensaje;

                System.out.println("[UDP] " + salida);

                broadcastUDP(
                    socket,
                    clientesUDP,
                    nombresUDP,
                    salida
                );
            }

        } catch (Exception e) {
            System.err.println("Error UDP: " + e.getMessage());
        }
    }
    
    /**
     * Envía un mensaje UDP a todos los clientes conectados.
     *
     * @param socket Socket UDP utilizado para el envío.
     * @param clientesUDP Conjunto de clientes conectados.
     * @param nombresUDP Mapa de nombres de usuarios.
     * @param mensaje Mensaje a enviar.
     * @throws IOException Si ocurre un error durante el envío.
     */
    private void broadcastUDP(DatagramSocket socket,Set<String> clientesUDP,
        Map<String, String> nombresUDP,String mensaje) throws IOException {

        byte[] data = mensaje.getBytes();

        for (String c : clientesUDP) {

            String[] parts = c.split(":");

            InetAddress cIP = InetAddress.getByName(parts[0]);
            int cPort = Integer.parseInt(parts[1]);

            DatagramPacket out = new DatagramPacket(
                data,
                data.length,
                cIP,
                cPort
            );

            socket.send(out);
        }
    }

    /**
     * Método principal del programa.
     *
     * Inicializa la ejecución del programa.
     * Permite seleccionar el protocolo de comunicación
     * (TCP o UDP) e inicia el servidor correspondiente.
     *
     * @param args Argumentos de línea de comandos.
     */
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        int opcion = 0;

        while (true) {

            System.out.println("Seleccione protocolo:");
            System.out.println("1. TCP");
            System.out.println("2. UDP");
            System.out.print("> ");

            try {
                opcion = Integer.parseInt(scan.nextLine());
                if (opcion == 1 || opcion == 2) {
                    break;
                }
                System.out.println("Opción inválida.\n");
            } catch (NumberFormatException e) {
                System.out.println("Debes ingresar un número válido.\n");
            }
        }

        TCPServer server = new TCPServer();
        switch (opcion) {
            case 1:
                server.startTCP();
                break;
            case 2:
                server.startUDP();
                break;
        }
        scan.close();
    }
}