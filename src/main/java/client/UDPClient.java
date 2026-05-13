/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;

/**
 * Cliente de chat que implementa comunicación mediante
 * el protocolo UDP.
 *
 * El cliente permite enviar y recibir mensajes
 * utilizando datagramas UDP hacia un servidor.
 *
 * También administra:
 * Registro de usuario.
 * Recepción asíncrona de mensajes.
 * Validación de conexión.
 * Desconexión del chat.
 *
 * La recepción de mensajes se realiza mediante
 * un hilo independiente.
 *
 * @author abigomez
 */
public class UDPClient {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 7777;

    private DatagramSocket socket;
    private boolean running;
    private Thread receiveThread;

    /**
     * Constructor de la clase UDPClient.
     *
     * Inicializa el socket UDP utilizando el puerto local
     * especificado por el usuario.
     *
     * @param localPort Puerto local utilizado por el cliente.
     * @throws SocketException Si ocurre un error al abrir el socket.
     */
    public UDPClient(int localPort) throws SocketException {
        socket = new DatagramSocket(localPort);
        running = false;
    }

    /**
     * Inicia un hilo para recibir mensajes del servidor
     * de manera asíncrona.
     *
     * El hilo permanece escuchando datagramas UDP mientras
     * el cliente esté en ejecución.
     *
     * También procesa mensajes especiales enviados
     * por el servidor, como:
     * servidor_lleno
     * usuario_existente
     * usuario_invalido
     */
    public void startReceiving() {
        running = true;

        // Hilo encargado de escuchar mensajes UDP enviados por el servidor.
        receiveThread = new Thread(() -> {
            byte[] buffer = new byte[65507];

            while (running) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                try {
                    // Espera bloqueante hasta recibir un datagrama.
                    socket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());
                    
                    if(message.equals("servidor_lleno")){

                        System.out.println("\n[SERVIDOR] El servidor está lleno.");
                        System.out.println("Desconectando cliente...");

                        stop();

                        System.exit(0);
                    }
                    
                    if(message.equals("usuario_existente")){

                         System.out.println("\n[SERVIDOR] Ese nombre ya existe.");
                        stop();
                        System.exit(0);
                    }
                    
                    
                    if(message.equals("usuario_invalido")){

                        System.out.println(
                            "\n[SERVIDOR] Nombre inválido.\n" +
                            "Solo letras y números (3-15 caracteres)."
                        );

                        stop();
                        System.exit(0);
                    }
                    
                    System.out.println("\n" + message);
                    System.out.print("> ");

                } catch (Exception e) {
                    if (running) {
                        System.err.println("Error al recibir: " + e.getMessage());
                    }
                }
            }
        });

        receiveThread.start();
    }

    /**
     * Envía un mensaje al servidor UDP.
     *
     * El mensaje es encapsulado en un DatagramPacket
     * y enviado a la dirección y puerto del servidor.
     *
     * @param message Mensaje que será enviado.
     */
    public void sendMessage(String message) {
        try {
            InetAddress address = InetAddress.getByName(SERVER_IP);
            byte[] data = message.getBytes();

            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    address,
                    SERVER_PORT
            );

            socket.send(packet);

        } catch (Exception e) {
            System.err.println("Error al enviar: " + e.getMessage());
        }
    }

    /**
     * Finaliza la ejecución del cliente UDP.
     *
     * El método detiene el hilo receptor,
     * cierra el socket UDP y libera los recursos
     * utilizados por el cliente.
     */
    public void stop() {
        running = false;

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        try {
            if (receiveThread != null) {
                receiveThread.join(1000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("Cliente UDP desconectado.");
    }

    /**
     * Método principal del cliente UDP.
     *
     * Permite al usuario:
     * Configurar el puerto local.
     * Registrarse en el servidor.
     * Enviar mensajes.
     * Recibir mensajes del chat.
     * Desconectarse mediante el comando "exit".
     *
     * @param args Argumentos de línea de comandos.
     */
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== CLIENTE UDP ===");

        System.out.print("Ingresa tu puerto local: ");
        int localPort = scanner.nextInt();
        scanner.nextLine();

        UDPClient client;

        try {
            client = new UDPClient(localPort);
            client.startReceiving();

        } catch (SocketException e) {
            System.out.println("No se pudo abrir el puerto.");
            return;
        }

        System.out.print("Ingresa tu nombre: ");
        String nombre = scanner.nextLine().trim();

        client.sendMessage(nombre);

        System.out.println("\n--- Comandos ---");
        System.out.println("mensaje → enviar");
        System.out.println("exit → salir");
        System.out.println();

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                client.sendMessage("exit");
                client.stop();
                break;
            } else if (!input.isEmpty()) {
                client.sendMessage(input);
            }
        }

        scanner.close();
    }

}
