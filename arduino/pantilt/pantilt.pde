/* 
  Pantilt head
  
  The pantilt head is a head with 2 degrees of freedom (yaw and pitch) whose 
  primary purpose is to enable a user to capture a panorama using a phone. 
  The phone is communicating to the pantilt head via bluetooth.
  
  author: rom@google.com (rom clement)
  
 */
 
 
#include <Servo.h>
#include "SerReadStr.h"
 
const long BAUD_RATE = 115200;
const int BUFFER_SIZE = 20; // and that's probably too much

const int SERVO1 = 11;  // The servo for the yaw direction
const int SERVO2 = 12;  // The servo for the pitch direction

const char COMMAND_YAW = 'Y';
const char COMMAND_PITCH = 'P';
const char COMMAND_VERSION = 'V';
const char COMMAND_DEBUG = 'D'; // Returns logging messages
int DEBUG_STATUS = 0;
const char VERSION_NUMBER[4] = "1.0";
const char COMMAND_UP_SINCE = 'T'; //Duration in ms since the system is up.
 
long start_time = millis();
 
Servo servo_yaw;
Servo servo_pitch;
 
int MoveServo(Servo myservo, int angle_deg) {
  myservo.write(angle_deg);
  return 0;
}

void SerialListener(char *buf, int sizebuf) {
  int rc = SerialReadString::read_cr_terminated(buf, sizebuf*sizeof(*buf), 10);
  switch (rc) {
  case -2: // no commands sent
    break;
  case -1:
    Serial.print('\n');
    Serial.print("buffer overflow");
    break;
  default:
    if (DEBUG_STATUS == 1) {
      Serial.print('\n');
      Serial.print("Received packet: \"");
      Serial.print(buf);
      Serial.print("\""); 
    }
  }
}
 
void init_servos() {
  servo_yaw.attach(SERVO1);
  servo_pitch.attach(SERVO2);  
}
 
void setup() {
  Serial.begin(BAUD_RATE);
  Serial.flush();
  init_servos();
  Serial.print("\r\nBoard is setup. You can now send a command.");
 }
 
void loop() {
  
  char buf_listener[BUFFER_SIZE];  
  buf_listener[0] = '\0';
  SerialListener(buf_listener, BUFFER_SIZE);
  int value = -1;  // value of the command
  char command = buf_listener[0];
  
  switch (command) {
        
    case COMMAND_YAW:  // Move servo of yaw axis
      value = atoi(buf_listener+1);
      if (DEBUG_STATUS == 1) {
        Serial.print("\nMoving yaw servo by [degrees]: ");
        Serial.print(value);
        Serial.print('\n');
      }
      MoveServo(servo_yaw, value);
      break;
      
    case COMMAND_PITCH:  //Mover servo of pitch axis
      value = atoi(buf_listener+1);
      if (DEBUG_STATUS == 1) {
        Serial.print("\nMoving pitch servo by [degrees]: ");
        Serial.print(value);
        Serial.print('\n');
      }
      MoveServo(servo_pitch, value);
      break;
      
    case COMMAND_VERSION: //Print the firmware version
      Serial.print("\nVersion: ");
      Serial.print(VERSION_NUMBER);
      Serial.print('\n');
      break;
      
    case COMMAND_DEBUG:  //Print logging messages
      DEBUG_STATUS = atoi(buf_listener+1);
      Serial.print("\nChanging verbosity to: ");
      Serial.print(DEBUG_STATUS);
      Serial.print('\n');
      break;
      
    case COMMAND_UP_SINCE:
      long duration = millis() - start_time;
      Serial.print("\nUptime [ms]: ");
      Serial.print(duration);
      Serial.print('\n');
    }  
    
 }
