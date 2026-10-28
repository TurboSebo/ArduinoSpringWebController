package io.github.turbosebo.arduinospringwebcontroller.service;

import com.fazecast.jSerialComm.SerialPort;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
public class ArduinoService {

    private final ArduinoCommunicator communicator;
    private final ArduinoConnectionVerifier verifier;

    private volatile ConnectionState currentState = ConnectionState.DISCONNECTED;
    private volatile String detectedPort = "";
    private volatile String deviceIdn = "";
    private volatile String lastErrorMessage = "";
    private volatile String lastReceivedMessage = "";

    public record PortInfo(String systemPortName, String descriptivePortName) {}

    public ArduinoService(ArduinoCommunicator communicator, ArduinoConnectionVerifier verifier) {
        this.communicator = communicator;
        this.verifier = verifier;

        // Podpięcie callbacków z Communicatora
        this.communicator.setErrorCallback(err -> updateState(ConnectionState.ERROR, err));
        this.communicator.setDataReceivedCallback(msg -> this.lastReceivedMessage = msg);
    }

    public List<PortInfo> getAvailablePorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(p -> new PortInfo(p.getSystemPortName(), p.getDescriptivePortName()))
                .collect(Collectors.toList());
    }

    public synchronized boolean connectToPort(String portName) {
        communicator.disconnect();
        System.out.println("Próba połączenia z portem: " + portName);
        this.detectedPort = portName;
        this.deviceIdn = "";
        updateState(ConnectionState.VERIFYING, "");

        ArduinoConnectionVerifier.VerificationResult result = verifier.verifyAndConnect(portName);

        if (result.success() && result.port() != null) {
            boolean commStarted = communicator.connect(result.port());
            if (commStarted) {
                this.deviceIdn = result.response();
                updateState(ConnectionState.CONNECTED, "");
                return true;
            } else {
                result.port().closePort();
                updateState(ConnectionState.ERROR, "Błąd: Nie można uruchomić ArduinoCommunicator.");
                return false;
            }
        } else {
            updateState(ConnectionState.ERROR, result.errorMessage());
            return false;
        }
    }

    public synchronized void disconnect() {
        communicator.disconnect();
        this.deviceIdn = "";
        updateState(ConnectionState.DISCONNECTED, "");
        System.out.println("Rozłączono port szeregowy.");
    }

    /**
     * Wysyła komendę do Arduino (np. "LED:1", "DAC:2000", "READV?")
     */
    public void send(String command) {
        if (currentState == ConnectionState.CONNECTED && communicator.isConnected()) {
            communicator.sendData(command);
        } else {
            throw new IllegalStateException("Brak połączenia z Arduino. Nie można wysłać komendy: " + command);
        }
    }

    public void setLedState(boolean on) {
        send(ArduinoCommands.setLed(on));
    }

    /**
     * Pozwala podmienić listener wiadomości (przyda się przy WebSocketach!)
     */
    public void setMessageListener(Consumer<String> listener) {
        communicator.setDataReceivedCallback(msg -> {
            this.lastReceivedMessage = msg;
            if (listener != null) listener.accept(msg);
        });
    }

    private void updateState(ConnectionState newState, String errorMessage) {
        this.currentState = newState;
        this.lastErrorMessage = errorMessage != null ? errorMessage : "";
    }

    public ConnectionState getCurrentState() { return currentState; }
    public String getDetectedPort() { return detectedPort; }
    public String getDeviceIdn() { return deviceIdn; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public String getLastReceivedMessage() { return lastReceivedMessage; }

    @PreDestroy
    public void cleanup() {
        disconnect();
    }
}