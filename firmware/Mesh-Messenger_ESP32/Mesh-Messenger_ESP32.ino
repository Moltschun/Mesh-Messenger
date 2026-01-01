#include <SPI.h>
#include <LoRa.h>

#define SCK 18
#define MISO 19
#define MOSI 23
#define SS 5
#define RST 22
#define DIO0 21

void setup() 
{
  Serial.begin(115200);
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
  if (LoRa.parsePacket())
  {
    uint8_t receiver = LoRa.read();
    String message = "";
    while (LoRa.available())
    {
      message += (char)LoRa.read();
      Serial.println(message);
    }
    LoRa.beginPacket();
    LoRa.write(receiver);
    LoRa.print(message);         // Само сообщение
    LoRa.endPacket();
    Serial.println("Сообщение ретранслировано");
  }
}