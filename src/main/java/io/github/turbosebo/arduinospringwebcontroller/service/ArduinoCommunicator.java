package io.github.turbosebo.arduinospringwebcontroller.service;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

@Component
public class ArduinoCommunicator {
    private static final int BAUD_RATE = 9600;
    private SerialPort commPort;
    private OutputStream output;
    private BufferedReader reader;
    private Consumer<String> errorCallback;
    private Consumer<String> dataReceivedCallback;
    private Thread listenerThread;
    private volatile boolean isListening = false;

    public void setErrorCallback(Consumer<String> errorCallback) {
        this.errorCallback = errorCallback;
    }

    public void setDataReceivedCallback(Consumer<String> dataReceivedCallback) {
        this.dataReceivedCallback = dataReceivedCallback;
    }

    public synchronized boolean connect(SerialPort port) {
        try {
            if (isConnected()) disconnect();
            this.commPort = port;
            commPort.setBaudRate(BAUD_RATE);

            // SEMI_BLOCKING ustawiony na 100ms
            commPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

            InputStream in = commPort.getInputStream();
            this.output = commPort.getOutputStream();
            this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));

            System.out.println("Communicator: Start na porcie " + commPort.getSystemPortName());
            startListening();
            return true;
        } catch (Exception e) {
            if (errorCallback != null) errorCallback.accept("Błąd connect(): " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public synchronized void sendData(String data) {
        if (!isConnected()) {
            throw new IllegalStateException("Brak aktywnego połączenia z portem szeregowym.");
        }
        try {
            String framed = data.endsWith("\n") ? data : (data + "\n");
            output.write(framed.getBytes(StandardCharsets.US_ASCII));
            output.flush();
            System.out.println("TX >> " + framed.trim());
        } catch (Exception e) {
            handleConnectionError("Błąd wysyłania: " + e.getMessage());
            throw new RuntimeException("Błąd wysyłania danych przez UART: " + e.getMessage(), e);
        }
    }

    public synchronized void startListening() {
        if (isListening) return;
        isListening = true;
        listenerThread = new Thread(() -> {
            while (isListening && isConnected()) {
                try {
                    String line = reader.readLine();

                    if (line != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            System.out.println("RX << " + line);
                            if (dataReceivedCallback != null) {
                                dataReceivedCallback.accept(line);
                            }
                        }
                    }
                } catch (IOException e) {
                    String msg = e.getMessage();
                    if (msg != null && msg.toLowerCase().contains("timed out")) {
                        continue; // Normalny timeout 100ms w SEMI_BLOCKING
                    }
                    if (isListening) handleConnectionError("Błąd IO Listenera: " + e.getMessage());
                } catch (Exception e) {
                    if (isListening) handleConnectionError("Błąd Listenera: " + e.getMessage());
                }
            }
        }, "ArduinoListenerThread");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void disconnect() {
        isListening = false;
        try {
            if (commPort != null && commPort.isOpen()) {
                commPort.closePort();
            }
        } catch (Exception e) { /* ignore */ }
        commPort = null;
        reader = null;
        output = null;
    }

    public synchronized boolean isConnected() {
        return commPort != null && commPort.isOpen();
    }

    private void handleConnectionError(String errorMessage) {
        if (!isListening) return;
        System.err.println(errorMessage);
        if (errorCallback != null) {
            errorCallback.accept("Rozłączono: " + errorMessage);
        }
        disconnect();
    }
}