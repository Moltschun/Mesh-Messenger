/*
 * MeshMessenger_Node2.ino
 * Прошивка для ВТОРОГО узла (NODE_ID = 2).
 * Протокол полностью совместим с Node 1.
 */

#include "BluetoothSerial.h"
#include <SPI.h>
#include <LoRa.h>

// --- Пины подключения (ESP32 / TTGO LoRa32) ---
#define SCK_PIN   18
#define MISO_PIN  19
#define MOSI_PIN  23
#define SS_PIN    5
#define RST_PIN   22
#define DIO0_PIN  17 

// --- Настройки Узла ---
#define NODE_ID 2       // <--- ЭТО ВТОРОЙ УЗЕЛ
#define DEST_ID 1       // <--- Шлем на Первый (по умолчанию)

// --- Bluetooth ---
BluetoothSerial SerialBT;
const char* bt_name = "MeshNode_2"; // <--- Уникальное имя Bluetooth

// --- Переменные Mesh-сети ---
uint8_t msgIdCounter = 0; 
#define HISTORY_SIZE 100
uint8_t seenMessages[HISTORY_SIZE][2]; 
int historyIndex = 0; 

void setup() {
  Serial.begin(115200);
  
  // 1. Запуск Bluetooth
  SerialBT.begin(bt_name);
  Serial.println("Bluetooth (Node 2) запущен.");

  // 2. Запуск LoRa
  SPI.begin(SCK_PIN, MISO_PIN, MOSI_PIN, SS_PIN);
  LoRa.setPins(SS_PIN, RST_PIN, DIO0_PIN);
  
  if (!LoRa.begin(433E6)) {
    Serial.println("[ОШИБКА] Не удалось запустить LoRa!");
    while (1);
  }
  
  LoRa.setSyncWord(0x12); 
  Serial.println("LoRa инициализирована (433 МГц)");
}

// Проверка дубликатов
bool isMessageSeen(uint8_t msgId, uint8_t senderId) {
  for (int i = 0; i < HISTORY_SIZE; i++) {
    if (seenMessages[i][0] == msgId && seenMessages[i][1] == senderId) {
      return true; 
    }
  }
  return false;
}

// Запись в историю
void markMessageAsSeen(uint8_t msgId, uint8_t senderId) {
  seenMessages[historyIndex][0] = msgId;
  seenMessages[historyIndex][1] = senderId;
  historyIndex++;
  if (historyIndex >= HISTORY_SIZE) historyIndex = 0; 
}

void loop() {
  // --- ИСХОДЯЩИЕ: Bluetooth -> LoRa ---
  if (SerialBT.available()) {
    String msgText = SerialBT.readStringUntil('\n');
    msgText.trim(); 
    
    if (msgText.length() > 0) {
      msgIdCounter++; 
      
      Serial.print("[ОТПРАВКА] От Node 2 к ID:");
      Serial.print(DEST_ID);
      Serial.println(msgText);

      LoRa.beginPacket();
      LoRa.write(msgIdCounter); 
      LoRa.write(DEST_ID);      
      LoRa.write(NODE_ID);      
      LoRa.print(msgText);      
      LoRa.endPacket();

      // ВАЖНО: Подтверждение для Android (чтобы появилась галочка)
      SerialBT.println("ESP32 получила"); 
      
      markMessageAsSeen(msgIdCounter, NODE_ID);
    }
  }

  // --- ВХОДЯЩИЕ: LoRa -> Bluetooth ---
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    uint8_t msgId    = LoRa.read();
    uint8_t receiver = LoRa.read();
    uint8_t sender   = LoRa.read();
    
    String message = "";
    while (LoRa.available()) {
      message += (char)LoRa.read();
    }

    Serial.print("[РАДИО] Принят пакет от: ");
    Serial.println(sender);

    if (isMessageSeen(msgId, sender)) return; 
    markMessageAsSeen(msgId, sender);

    if (receiver == NODE_ID) {
      // Сообщение для меня (Node 2)
      SerialBT.println(message);
    } 
    else if (sender != NODE_ID) {
      // Ретрансляция
      LoRa.beginPacket();
      LoRa.write(msgId);    
      LoRa.write(receiver); 
      LoRa.write(sender);   
      LoRa.print(message);  
      LoRa.endPacket();
    }
  }
}