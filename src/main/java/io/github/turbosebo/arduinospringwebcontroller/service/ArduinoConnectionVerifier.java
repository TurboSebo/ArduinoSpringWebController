package io.github.turbosebo.arduinospringwebcontroller.service;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ArduinoConnectionVerifier {

    private static final int BAUD_RATE = 9600;
    private static final String IDENTIFY_COMMAND = "*IDN?\n";
    private static final String EXPECTED_RESPONSE_PREFIX = "MY_FESG_ARDUINO_V1.1_WAVE";
    private static final int READ_TIMEOUT_MS = 200;
    private static final int TOTAL_RESPONSE_TIMEOUT_MS = 2500;

    public record VerificationResult(boolean success, SerialPort port, String response, String errorMessage) {}

    public VerificationResult verifyAndConnect(String portName) {
        SerialPort commPort = null;
        boolean success = false;
        InputStream input = null;
        OutputStream output = null;
        String response = "";
        String errorMsg = "";

        try {
            commPort = SerialPort.getCommPort(portName);
            commPort.setBaudRate(BAUD_RATE);

            if (!commPort.openPort(2000)) {
                throw new IllegalStateException("Nie można otworzyć portu szeregowego: " + portName);
            }

            commPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0);

            // Odczekanie na restart bootloadera Arduino Uno po otwarciu portu (sygnał DTR)
            Thread.sleep(1800);

            input = commPort.getInputStream();
            output = commPort.getOutputStream();

            commPort.flushIOBuffers();
            output.write(IDENTIFY_COMMAND.getBytes(StandardCharsets.US_ASCII));
            output.flush();

            ByteArrayOutputStream buffer = new ByteArrayOutputStream(128);
            byte[] tmp = new byte[64];
            long deadline = System.currentTimeMillis() + TOTAL_RESPONSE_TIMEOUT_MS;

            while (System.currentTimeMillis() < deadline) {
                int read = input.read(tmp);
                if (read > 0) {
                    buffer.write(tmp, 0, read);
                    byte[] arr = buffer.toByteArray();
                    for (byte b : arr) {
                        if (b == '\n' || b == '\r') {
                            deadline = 0;
                            break;
                        }
                    }
                }
            }

            response = new String(buffer.toByteArray(), StandardCharsets.US_ASCII).trim();

            if (!response.isEmpty() && response.startsWith(EXPECTED_RESPONSE_PREFIX)) {
                success = true;
                System.out.println("Weryfikacja OK: " + response);
            } else {
                errorMsg = "Nieprawidłowa odpowiedź *IDN?: '" + response + "' (oczekiwano prefiksu: " + EXPECTED_RESPONSE_PREFIX + ")";
                System.err.println(errorMsg);
            }

        } catch (Exception e) {
            errorMsg = "Wyjątek weryfikacji: " + e.getMessage();
            System.err.println(errorMsg);
            success = false;
        } finally {
            // Zamykamy strumienie i port TYLKO jeśli weryfikacja się NIE powiodła.
            // Jeśli się powiodła -> zostawiamy otwarte dla ArduinoCommunicator!
            if (!success) {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                    if (commPort != null && commPort.isOpen()) commPort.closePort();
                } catch (Exception ex) { /* ignoruj */ }
                commPort = null;
            }
        }

        return new VerificationResult(success, commPort, response, errorMsg);
    }
}