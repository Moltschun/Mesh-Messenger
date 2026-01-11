/*
 * MeshMessenger_ESP32.ino
 * * Main firmware for ESP32 LoRa Node.
 * Features:
 * - Bluetooth Classic Serial (Smartphone <-> ESP32)
 * - LoRa Mesh Protocol (Flood algorithm with deduplication)
 * - Message Relaying
 */

#include "BluetoothSerial.h"
#include <SPI.h>
#include <LoRa.h>

// --- Hardware Pins (ESP32 / TTGO LoRa32) ---
#define SCK_PIN   18
#define MISO_PIN  19
#define MOSI_PIN  23
#define SS_PIN    5
#define RST_PIN   22
#define DIO0_PIN  17 // May vary depending on board revision (sometimes 26)

// --- Node Configuration ---
#define NODE_ID 1       // Unique ID of this device
#define DEST_ID 2       // Default destination (broadcast or specific)

// --- Bluetooth ---
BluetoothSerial SerialBT;
const char* bt_name = "MeshNode_1";

// --- Mesh Variables ---
uint8_t msgIdCounter = 0; // Local counter for sent messages

// Buffer for deduplication (history of seen messages)
// Stores: [MessageID, SenderID]
#define HISTORY_SIZE 100
uint8_t seenMessages[HISTORY_SIZE][2]; 
int historyIndex = 0; 

void setup() {
  Serial.begin(115200);
  
  // 1. Init Bluetooth
  SerialBT.begin(bt_name);
  Serial.println("Bluetooth Started. Ready to pair.");

  // 2. Init LoRa
  SPI.begin(SCK_PIN, MISO_PIN, MOSI_PIN, SS_PIN);
  LoRa.setPins(SS_PIN, RST_PIN, DIO0_PIN);
  
  if (!LoRa.begin(433E6)) {
    Serial.println("[ERROR] LoRa init failed!");
    while (1);
  }
  
  LoRa.setSyncWord(0x12); // Private network key
  Serial.println("LoRa Initialized (433 MHz)");
}

// Function to check if message was already handled
bool isMessageSeen(uint8_t msgId, uint8_t senderId) {
  for (int i = 0; i < HISTORY_SIZE; i++) {
    if (seenMessages[i][0] == msgId && seenMessages[i][1] == senderId) {
      return true; // Found duplicate
    }
  }
  return false;
}

// Function to add message to history (Ring Buffer)
void markMessageAsSeen(uint8_t msgId, uint8_t senderId) {
  seenMessages[historyIndex][0] = msgId;
  seenMessages[historyIndex][1] = senderId;
  
  historyIndex++;
  if (historyIndex >= HISTORY_SIZE) {
    historyIndex = 0; // Overwrite oldest records
  }
}

void loop() {
  // -------------------------------------------------
  // 1. OUTGOING: Bluetooth -> LoRa
  // -------------------------------------------------
  if (SerialBT.available()) {
    String msgText = SerialBT.readStringUntil('\n');
    msgText.trim();
    
    if (msgText.length() > 0) {
      msgIdCounter++; // Increment ID for new message
      
      Serial.print("[SENDING] To ID:");
      Serial.print(DEST_ID);
      Serial.print(" Msg: ");
      Serial.println(msgText);

      LoRa.beginPacket();
      LoRa.write(msgIdCounter); // Byte 1: Message ID
      LoRa.write(DEST_ID);      // Byte 2: Destination
      LoRa.write(NODE_ID);      // Byte 3: Sender (Me)
      LoRa.print(msgText);      // Payload
      LoRa.endPacket();

      // Send ACK back to Phone (to show checkmark)
      SerialBT.println("ESP получила"); 
      
      // Add my own message to history so I don't relay it back to myself
      markMessageAsSeen(msgIdCounter, NODE_ID);
    }
  }

  // -------------------------------------------------
  // 2. INCOMING: LoRa -> Bluetooth (or Relay)
  // -------------------------------------------------
  int packetSize = LoRa.parsePacket();
  if (packetSize) {
    // --- Header Parsing ---
    uint8_t msgId    = LoRa.read();
    uint8_t receiver = LoRa.read();
    uint8_t sender   = LoRa.read();
    
    // --- Payload Reading ---
    String message = "";
    while (LoRa.available()) {
      message += (char)LoRa.read();
    }

    Serial.print("[RADIO] RX packet. Sender:");
    Serial.print(sender);
    Serial.print(" MsgID:");
    Serial.println(msgId);

    // --- Deduplication Check ---
    if (isMessageSeen(msgId, sender)) {
      Serial.println("   -> Duplicate. Ignored.");
      return; // Skip rest of the loop
    }

    // Mark as seen immediately
    markMessageAsSeen(msgId, sender);

    // --- Routing Logic ---
    if (receiver == NODE_ID) {
      // CASE A: Message for ME
      Serial.println("   -> For me! Sending to Phone.");
      SerialBT.println(message);
    } 
    else if (sender != NODE_ID) {
      // CASE B: Not for me, and not sent by me -> RELAY
      Serial.println("   -> Relaying...");
      
      LoRa.beginPacket();
      LoRa.write(msgId);    // Keep original ID
      LoRa.write(receiver); // Keep original Dest
      LoRa.write(sender);   // Keep original Sender
      LoRa.print(message);  // Keep original Text
      LoRa.endPacket();
    }
  }
}