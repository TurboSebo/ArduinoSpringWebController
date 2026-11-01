const $ = (id) => document.getElementById(id);
const api = "/api/arduino";
let connected = false;

async function request(path, options = {}) {
    const response = await fetch(api + path, options);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.message || `Żądanie zakończone błędem (${response.status})`);
    return data;
}

function showMessage(element, text, type = "") {
    element.textContent = text || "";
    element.className = `message ${type}`;
}

function updateStatus(data) {
    const state = data.state || "DISCONNECTED";
    connected = state === "CONNECTED";
    $("statusText").textContent = connected ? "Połączono" : state === "ERROR" ? "Błąd połączenia" : "Rozłączono";
    $("statusDot").className = `dot ${connected ? "connected" : state === "ERROR" ? "error" : ""}`;
    $("stateValue").textContent = state;
    $("portValue").textContent = data.port || "—";
    $("deviceValue").textContent = data.deviceId || "—";
    $("lastMessage").textContent = `Ostatnia odpowiedź: ${data.lastReceived || "—"}`;
    $("connectButton").disabled = connected || !$("portSelect").value;
    $("disconnectButton").disabled = !connected;
    $("ledOnButton").disabled = !connected;
    $("ledOffButton").disabled = !connected;
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"']/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" })[char]);
}

async function loadPorts() {
    const select = $("portSelect");
    try {
        const ports = await request("/ports");
        select.innerHTML = ports.length
            ? ports.map((port) => `<option value="${escapeHtml(port.systemPortName)}">${escapeHtml(port.systemPortName)} — ${escapeHtml(port.descriptivePortName || "brak opisu")}</option>`).join("")
            : "<option value=''>Nie znaleziono portów</option>";
        $("connectButton").disabled = !select.value || connected;
    } catch (error) {
        select.innerHTML = "<option value=''>Nie można pobrać portów</option>";
        showMessage($("connectionMessage"), error.message, "error");
    }
}

async function refreshStatus() {
    try {
        updateStatus(await request("/status"));
    } catch (error) {
        showMessage($("connectionMessage"), error.message, "error");
    }
}

async function setLed(state) {
    try {
        const data = await request(`/led/${state}`, { method: "POST" });
        const isOn = state === 1;
        $("ledIndicator").classList.toggle("on", isOn);
        $("ledState").textContent = isOn ? "Dioda jest włączona" : "Dioda jest wyłączona";
        showMessage($("ledMessage"), `Odpowiedź urządzenia: ${data.deviceReply || "brak"}`, "success");
        await refreshStatus();
    } catch (error) {
        showMessage($("ledMessage"), error.message, "error");
    }
}

$("refreshButton").addEventListener("click", loadPorts);
$("portSelect").addEventListener("change", () => { $("connectButton").disabled = connected || !$("portSelect").value; });
$("connectButton").addEventListener("click", async () => {
    try {
        const data = await request(`/connect?port=${encodeURIComponent($("portSelect").value)}`, { method: "POST" });
        showMessage($("connectionMessage"), `Połączono z ${data.port}.`, "success");
        await refreshStatus();
    } catch (error) {
        showMessage($("connectionMessage"), error.message, "error");
        await refreshStatus();
    }
});
$("disconnectButton").addEventListener("click", async () => {
    try {
        await request("/disconnect", { method: "POST" });
        showMessage($("connectionMessage"), "Urządzenie zostało rozłączone.", "success");
        await refreshStatus();
    } catch (error) {
        showMessage($("connectionMessage"), error.message, "error");
    }
});
$("ledOnButton").addEventListener("click", () => setLed(1));
$("ledOffButton").addEventListener("click", () => setLed(0));

loadPorts();
refreshStatus();
setInterval(refreshStatus, 5000);
