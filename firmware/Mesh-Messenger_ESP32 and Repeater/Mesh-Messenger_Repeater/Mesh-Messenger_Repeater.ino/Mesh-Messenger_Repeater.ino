/*
 * MeshMessenger_Repeater.ino
 * Прошивка для РЕТРАНСЛЯТОРА (Repeater).
 * * Задача:
 * 1. Прослушивание эфира (433 МГц).
 * 2. Фильтрация дубликатов.
 * 3. Передача принятого пакета дальше без изменений.
 * * ВНИМАНИЕ: Bluetooth и Serial-управление здесь отключены для экономии энергии.
 */

#include <SPI.h>
#include <LoRa.h>

// --- Пины подключения ---
// Убедись, что DIO0 подключен к 21 (на TTGO V1.0 часто 26, на V2.0/V2.1 - 17 или 26)
#define SCK_PIN   18
#define MISO_PIN  19
#define MOSI_PIN  23
#define SS_PIN    5
#define RST_PIN   22
#define DIO0_PIN  21 

// --- Буфер истории (защита от зацикливания) ---
#define HISTORY_SIZE 100
uint8_t seenMessages[HISTORY_SIZE][2]; // [ID сообщения, ID отправителя]
int historyIndex = 0;

void setup() 
{
  // Инициализация Serial только для отладки
  Serial.begin(115200);
  Serial.println("Запуск Ретранслятора...");

  // Инициализация LoRa
  SPI.begin(SCK_PIN, MISO_PIN, MOSI_PIN, SS_PIN);
  LoRa.setPins(SS_PIN, RST_PIN, DIO0_PIN);
  
  if (!LoRa.begin(433E6)) {
    Serial.println("[ОШИБКА] LoRa не найдена!");
    while (1);
  }
  
  LoRa.setSyncWord(0x12);
  Serial.println("LoRa готова. Режим прослушивания.");
}

// Функция проверки дубликатов
bool isMessageSeen(uint8_t msgId, uint8_t senderId) {
  for (int i = 0; i < HISTORY_SIZE; i++) {
    if (seenMessages[i][0] == msgId && seenMessages[i][1] == senderId) {
      return true;
    }
  }
  return false;
}

// Запись сообщения в историю
void markMessageAsSeen(uint8_t msgId, uint8_t senderId) {
  seenMessages[historyIndex][0] = msgId;
  seenMessages[historyIndex][1] = senderId;
  historyIndex++;
  if (historyIndex >= HISTORY_SIZE) historyIndex = 0;
}

void loop() 
{
  // Если пришел пакет
  int packetSize = LoRa.parsePacket();
  if (packetSize) 
  {
    // 1. Читаем заголовок (Строго 3 байта, как в Node 1 и 2)
    uint8_t msgId      = LoRa.read(); // ID сообщения
    uint8_t receiverId = LoRa.read(); // Кому
    uint8_t senderId   = LoRa.read(); // От кого
    
    // 2. Читаем текст сообщения
    String message = "";
    while (LoRa.available()) {
      message += (char)LoRa.read();
    }

    Serial.print("[RX] Принято от ID: ");
    Serial.print(senderId);
    Serial.print(" MsgID: ");
    Serial.println(msgId);

    // 3. Проверка: видели ли мы это сообщение?
    if (isMessageSeen(msgId, senderId)) {
      Serial.println("   -> Дубликат. Игнорирую.");
      return; 
    }

    // 4. Запоминаем сообщение
    markMessageAsSeen(msgId, senderId);

    // 5. РЕТРАНСЛЯЦИЯ (Повторяем пакет в эфир)
    Serial.println("   -> Ретрансляция...");
    
    LoRa.beginPacket();
    LoRa.write(msgId);      // Сохраняем оригинальный ID
    LoRa.write(receiverId); // Сохраняем получателя
    LoRa.write(senderId);   // Сохраняем автора
    LoRa.print(message);    // Сохраняем текст
    LoRa.endPacket();
  }
}