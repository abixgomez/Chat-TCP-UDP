package client;
import java.io.*;
import java.net.*;
import java.util.Scanner;

/**
 * Cliente de chat que implementa comunicación mediante
 * el protocolo TCP.
 *
 * El cliente permite conectarse a un servidor,
 * enviar y recibir mensajes en tiempo real,
 * ejecutar comandos del chat y desconectarse
 * correctamente del servidor.
 *
 * La comunicación se realiza utilizando sockets TCP
 * y un hilo independiente para la recepción de mensajes.
 *
 * @author abigomez
 */
public class TCPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 7777;
    
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private Thread receiverThread;
    private boolean connected = false;
    
    public static String usernameIn;

    /**
     * Constructor de la clase TCPClient.
     *
     * Establece la conexión con el servidor TCP,
     * inicializa los flujos de entrada y salida
     * y prepara al cliente para enviar y recibir mensajes.
     *
     * @throws IOException Si ocurre un error durante
     * la conexión con el servidor.
     */
    public TCPClient() throws IOException {
        // El cliente inicia el proceso de conexión (Three-way handshake)
        socket = new Socket(SERVER_IP, SERVER_PORT);
        connected = true;

        // Configurar flujos de datos (Streams)
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);

        System.out.println("=== CLIENTE TCP CONECTADO ===");
        System.out.println("Conectado a: " + SERVER_IP + ":" + SERVER_PORT);
        
        Scanner scanner = new Scanner(System.in);
        System.out.println("Escribe 'exit' para desconectarte.\n");
    }

    /**
     * Inicia un hilo encargado de recibir mensajes
     * del servidor de forma asíncrona.
     *
     * El hilo permanece escuchando mensajes mientras
     * la conexión esté activa.
     */
    public void startReceiving() {
        receiverThread = new Thread(() -> {
            try {
                String response;
                // Mientras haya conexión, leer líneas del servidor
                while (connected && (response = in.readLine()) != null) {
                    System.out.println(response);
                    System.out.print("> "); // Mostrar prompt de nuevo
                }
            } catch (IOException e) {
                if (connected) {
                    System.err.println("\nConexión perdida con el servidor.");
                }
            }
        });
        receiverThread.start();
    }

    /**
     * Inicia un hilo encargado de recibir mensajes
     * del servidor de forma asíncrona.
     *
     * El hilo permanece escuchando mensajes mientras
     * la conexión esté activa.
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message); 
        }
    }
    
    /**
     * Envía el comando de salida al servidor
     * para finalizar la conexión del cliente.
     */
    public void sendExit(){
        if(out != null){
            out.println("exit");
        }
    }

    
    /**
     * Cierra la conexión del cliente.
     *
     * El método finaliza el hilo receptor,
     * cierra el socket y libera los recursos
     * utilizados por el cliente.
     */
    public void stop() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close(); // Cierra el stream y el socket
            }
            if (receiverThread != null) {
                receiverThread.join(1000);
            }
        } catch (Exception e) {
            System.err.println("Error al cerrar cliente: " + e.getMessage());
        }
        System.out.println("Cliente desconectado.");
    }

    /**
     * Método principal del cliente TCP.
     *
     * Permite al usuario:
     * Conectarse al servidor.
     * Registrar un nombre de usuario.
     * Enviar mensajes al chat.
     * Utilizar comandos disponibles.
     * Desconectarse del servidor.
     *
     * @param args Argumentos de línea de comandos.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        TCPClient client = null;

        try {
            client = new TCPClient();
            
            boolean valido = false;
            
            while(!valido){
                System.out.println("Registre su usuario: ");
                usernameIn = scanner.nextLine().trim();
                
                client.out.println(usernameIn);
                
                String respuesta = client.in.readLine();
                
                if(respuesta == null){
                    System.out.println("Error en la conexión con el servidor.");
                    return;
                }
                
                if("1".equals(respuesta)){
                    System.out.println("Bienvenido al chat.\n");
                    valido = true;
                } else if("0".equals(respuesta)){
                    System.out.println("Usuario inválido o ya existente.\n" +
                                       "Solo letras y números (3-15 caracteres).\n"
                    );
                }
            }
            
            client.startReceiving();

            System.out.println("--- Comandos ---");
            System.out.println("   <mensaje>    - Enviar al servidor");
            System.out.println("   exit         - Salir");
            System.out.println("Expulsar a usuario de servidor: /kick usuario");
            System.out.println("Mensaje privado: /msg usuario mensaje");
            System.out.println("Lista de usuarios conectados: /usuarios");
            System.out.println();

            while (true) {
                System.out.print("> ");
                String input = scanner.nextLine().trim();

                if (input.equalsIgnoreCase("exit")) {
                    client.sendMessage("exit"); // Aviso al server
                    client.stop();
                    break;
                } else if (!input.isEmpty()) {
                    client.sendMessage(input);
                }
            }

        } catch (IOException e) {
            System.err.println("Error de conexión: " + e.getMessage());
        } finally {
            if (client != null) client.stop();
            scanner.close();
        }
    }
}