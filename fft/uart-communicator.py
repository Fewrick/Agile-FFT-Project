## Some code snippets taken from https://github.com/sebasabes2/processinator-bootloader/blob/main/bootloader.py

import sys
import time
import serial
import serial.tools.list_ports

filepath = sys.argv[1]

def getPort():
  ports = list(filter(lambda x: "USB Serial Port" in x.description, serial.tools.list_ports.comports()))
  if (len(ports) == 0):
    print("Unable to find Serial Port")
    return
  if (len(ports) != 1):
    print("Found multiple ports:\n" + '\n'.join(map(lambda x: x.description, ports)))
    return
  return ports[0].device

port = getPort()
ser = serial.Serial(port, 115200, timeout=3)

for i in range(32):
  print(f"writing {i}")
  ser.write(bytearray([i]))


def readByte():
  waits = 0
  while ser.inWaiting() == 0:
    if waits == 100:
      print("Uart timed out")
      sys.exit(1)
    time.sleep(0.01)
    waits = waits + 1
  return ser.read()[0]

readbuffer = bytearray()
while len(readbuffer) < 32:
  readbuffer.append(readByte())

print(readbuffer)

ser.close()
