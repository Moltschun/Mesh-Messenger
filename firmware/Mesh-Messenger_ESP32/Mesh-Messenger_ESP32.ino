#include <WiFi.h>
#include "BluetoothSerial.h"
#include <SPI.h>
#include <LoRa.h>

#define SCK 18
#define MISO 19
#define MOSI 23
#define SS 5
#define RST 22
#define DIO0 17

#define NODE_ID 2  // Уникальный ID для каждого ESP32 (1, 2, 3...)
#define DEST_ID 1  // ID получателя (или 255 для broadcast)

BluetoothSerial SerialBT;

// Переменная для хранения последнего сообщения из Bluetooth
String bluetoothMessage = "";

//Имя Bluetooth
const char* bt_name = "MESH_NODE_2"; 

void setup() {
  Serial.begin(115200);

  // Настройка Bluetooth
  SerialBT.begin(bt_name); 
  Serial.print("Bluetooth запущен. Имя: ");
  Serial.println(bt_name);
  
  Serial.println("Ожидание Bluetooth сообщений...");

  SPI.begin(SCK, MISO, MOSI, SS);
  LoRa.setPins(SS, RST, DIO0);
  while (!LoRa.begin(433E6))
  {
    Serial.println("LoRa не запустился!");
    delay(1000);
  }
  LoRa.setSyncWord(0x12);
  Serial.println("LoRa запустился!");
}

void loop() {
  // Проверка данных, приходящих с Android по Bluetooth
  if (SerialBT.available()) {
    String msg = SerialBT.readStringUntil('\n'); 
    msg.trim(); // Удаляем пробелы и символы новой строки

    if (msg.length() > 0) {
      // ОБНОВЛЯЕМ переменную новым сообщением
      bluetoothMessage = msg;
      
      // Выводим в Serial для отладки
      Serial.print("Получено новое сообщение:");
      Serial.println(bluetoothMessage);
      
      // Подтверждение получения
      SerialBT.print(">> ESP32 получила");
      SerialBT.println(bluetoothMessage);
    }
    LoRa.beginPacket();
    LoRa.write(DEST_ID);
    LoRa.endPacket();
    Serial.println("Сообщение отправилось");
  }
  if (LoRa.parsePacket())
  {
    uint8_t receiver = LoRa.read();
    if (receiver == NODE_ID)
    {
      String message = "";
      while (LoRa.available()) {
        message += (char)LoRa.read();
      }
      SerialBT.print(">> Пришло сообщение: ");
      SerialBT.println(message);
      Serial.println(message);
    }
    else
    {
      String message = "";
      while (LoRa.available()) {
        message += (char)LoRa.read();
      }
      LoRa.beginPacket();
      LoRa.write(receiver);
      LoRa.print(message);         // Само сообщение
      LoRa.endPacket();
      Serial.println("Сообщение отправилось");
    }
  }
}