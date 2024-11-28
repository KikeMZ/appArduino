package com.charolitos.appbluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    // Variables Bluetooth
    private BluetoothAdapter bluetoothAdapter;
    private OutputStream outputStream;
    private InputStream inputStream;

    private EditText etSendData;
    private ArrayAdapter<String> deviceListAdapter;
    private TextView tvDataReceived;

    // UUID del HC-06
    private final UUID HC06_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;
    private static final int BLUETOOTH_PERMISSION_REQUEST_CODE = 101;

    // ActivityResultLauncher para manejar la solicitud de habilitar Bluetooth
    private final ActivityResultLauncher<Intent> enableBtLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK) {
                    // Bluetooth habilitado
                    Toast.makeText(this, "Bluetooth habilitado", Toast.LENGTH_SHORT).show();
                } else {
                    // El usuario no habilitó Bluetooth
                    Toast.makeText(this, "Bluetooth no habilitado", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar UI
        ListView lvDevices = findViewById(R.id.lvDevices);
        Button btnScan = findViewById(R.id.btnScan);
        Button btnSend = findViewById(R.id.btnSend);
        etSendData = findViewById(R.id.etSendData);
        tvDataReceived = findViewById(R.id.tvDataReceived);

        // Inicializar Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        deviceListAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lvDevices.setAdapter(deviceListAdapter);

        // Verificar permisos y solicitar permisos de Bluetooth y ubicación si es necesario
        if (hasLocationPermission()) {
            requestLocationPermission();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestBluetoothPermissions();
        }

        // Botón para buscar dispositivos
        btnScan.setOnClickListener(view -> scanDevices());

        // Botón para enviar datos
        btnSend.setOnClickListener(view -> sendData());

        // Asignar OnItemClickListener al ListView
        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            // Obtener la dirección del dispositivo seleccionado
            String deviceInfo = deviceListAdapter.getItem(position);
            assert deviceInfo != null;
            String deviceAddress = deviceInfo.substring(deviceInfo.indexOf("(") + 1, deviceInfo.indexOf(")"));
            connectToDevice(deviceAddress);
        });
    }

    private void connectToDevice(String address) {
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (hasBluetoothPermission()) {
                    requestBluetoothPermissions();
                    return;
                }
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            BluetoothSocket bluetoothSocket = device.createRfcommSocketToServiceRecord(HC06_UUID);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            inputStream  = bluetoothSocket.getInputStream();

            // Configurar AT al conectarse
            startATConfiguration();

            // Iniciar la escucha para recibir datos
            startListeningForData(inputStream);

            Toast.makeText(this, "Conexión establecida", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Error al conectar", Toast.LENGTH_SHORT).show();
        }
    }

    private void startATConfiguration() {
        // Comandos AT para configurar el HC-06
        String[] atCommands = new String[]{
                "AT",           // Verificar que está en modo AT
                "AT+NAME=DispositivoHC-06",  // Cambiar nombre
                "AT+PSWD=1234",        // Cambiar la contraseña
                "AT+VERSION"           // Verificar la versión
        };

        for (String command : atCommands) {
            sendATCommand(command);
            try {
                // Pausar para que el HC-06 pueda responder a cada comando
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // Después de configurar, podemos intercambiar datos con el dispositivo
        // Puedes enviar más comandos AT si lo necesitas, o pasar a la lógica de intercambio de datos.
    }

    private void sendATCommand(String command) {
        if (outputStream != null) {
            try {
                // Enviar comando AT al HC-06
                outputStream.write((command + "\r\n").getBytes());
                outputStream.flush();

                // Leer la respuesta del HC-06
                String response = readResponse();
                runOnUiThread(() -> tvDataReceived.append("Comando: " + command + "\nRespuesta: " + response + "\n"));
            } catch (IOException e) {
                Toast.makeText(this, "Error al enviar comando AT", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String readResponse() {
        StringBuilder response = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytes;
        try {
            // Leer la respuesta del HC-06
            bytes = inputStream.read(buffer);
            response.append(new String(buffer, 0, bytes));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return response.toString();
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private boolean hasBluetoothPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        }, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @RequiresApi(api = Build.VERSION_CODES.S)
    private void requestBluetoothPermissions() {
        // Verificar si el permiso de Bluetooth está concedido
        if (hasBluetoothPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
                    BLUETOOTH_PERMISSION_REQUEST_CODE); // Código de solicitud de permisos
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case BLUETOOTH_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permiso de Bluetooth concedido", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permiso de Bluetooth denegado", Toast.LENGTH_SHORT).show();
                }
                break;
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permiso de ubicación concedido", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show();
                }
                break;
        }
    }

    private void scanDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (hasBluetoothPermission()) {
                requestBluetoothPermissions();
                return;
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        deviceListAdapter.clear();
        if (!pairedDevices.isEmpty()) {
            for (BluetoothDevice device : pairedDevices) {
                deviceListAdapter.add(device.getName() + " (" + device.getAddress() + ")");
            }
        } else {
            Toast.makeText(this, "No hay dispositivos emparejados", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendData() {
        String data = etSendData.getText().toString();
        if (!data.isEmpty() && outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                Toast.makeText(this, "Datos enviados: " + data, Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Toast.makeText(this, "Error al enviar", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startListeningForData(InputStream inputStream) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    // Leer datos del flujo de entrada
                    bytes = inputStream.read(buffer);
                    String incomingData = new String(buffer, 0, bytes);

                    // Actualizar la UI con los datos recibidos
                    runOnUiThread(() -> tvDataReceived.append("Respuesta: " + incomingData + "\n"));
                } catch (IOException e) {
                    break;
                }
            }
        });

        thread.start();
    }
}
