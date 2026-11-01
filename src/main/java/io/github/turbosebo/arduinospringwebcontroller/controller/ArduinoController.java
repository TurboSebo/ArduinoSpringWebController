package io.github.turbosebo.arduinospringwebcontroller.controller;

import io.github.turbosebo.arduinospringwebcontroller.service.ArduinoService;
import io.github.turbosebo.arduinospringwebcontroller.service.ConnectionState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/arduino")
public class ArduinoController {

    private final ArduinoService arduinoService;

    public ArduinoController(ArduinoService arduinoService) {
        this.arduinoService = arduinoService;
    }

    @GetMapping("/ports")
    public ResponseEntity<List<ArduinoService.PortInfo>> getAvailablePorts() {
        return ResponseEntity.ok(arduinoService.getAvailablePorts());
    }

    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
                "state", arduinoService.getCurrentState(),
                "port", arduinoService.getDetectedPort(),
                "deviceId", arduinoService.getDeviceIdn(),
                "lastReceived", arduinoService.getLastReceivedMessage(),
                "lastError", arduinoService.getLastErrorMessage()
        ));
    }

    @PostMapping("/connect")
    public ResponseEntity<?> connect(@RequestParam String port) {
        long startTime = System.nanoTime();
        boolean connected = arduinoService.connectToPort(port);
        double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;

        if (connected) {
            return ResponseEntity.ok(Map.of(
                    "status", ConnectionState.CONNECTED,
                    "port", port,
                    "deviceId", arduinoService.getDeviceIdn(),
                    "executionTimeMs", durationMs
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", arduinoService.getCurrentState(),
                    "message", arduinoService.getLastErrorMessage()
            ));
        }
    }

    @PostMapping("/disconnect")
    public ResponseEntity<?> disconnect() {
        arduinoService.disconnect();
        return ResponseEntity.ok(Map.of(
                "status", arduinoService.getCurrentState()
        ));
    }

    @PostMapping("/led/{state}")
    public ResponseEntity<?> controlLed(@PathVariable int state) {
        if (state != 0 && state != 1) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Stan diody musi wynosić 0 (wyłącz) lub 1 (włącz)."
            ));
        }

        long startTime = System.nanoTime();
        try {
            arduinoService.setLedState(state == 1);
            // Daj wątkowi nasłuchującemu czas na odebranie odpowiedzi z portu szeregowego.
            Thread.sleep(15);
            double durationMs = (System.nanoTime() - startTime) / 1_000_000.0;

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "ledState", state,
                    "deviceReply", arduinoService.getLastReceivedMessage(),
                    "backendTimeMs", durationMs
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", e.getMessage()
            ));
        }
    }
}
