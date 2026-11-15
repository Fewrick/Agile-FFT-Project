## Some code snippets taken from https://github.com/sebasabes2/processinator-bootloader/blob/main/bootloader.py

import sys
import time
import serial
import serial.tools.list_ports

import argparse

parser = argparse.ArgumentParser()
parser.add_argument("-i", "--input", required=True, help="input file")
group = parser.add_mutually_exclusive_group(required=True)
group.add_argument("-o", "--output", help="output file")
group.add_argument("-x", "--expected", help="expected output")
# TODO: add port argument
args = parser.parse_args()

def parsehexfile(path):
  file = open(path, "r")
  lines = file.readlines()
  return bytearray(map(lambda line: int(line, 16), lines))

# parse write buffer input file
writebuffer = parsehexfile(args.input)

# parse expect buffer input file if given
if args.expected != None:
  expectbuffer = parsehexfile(args.expected)

# get port
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

# open serial connection
ser = serial.Serial(port, 115200, timeout=3)

# write buffer
ser.write(writebuffer)

def readByte():
  waits = 0
  while ser.inWaiting() == 0:
    if waits == 100:
      print("Uart timed out")
      sys.exit(1)
    time.sleep(0.01)
    waits = waits + 1
  return ser.read()[0]

# read buffer
readbuffer = bytearray()
while len(readbuffer) < len(writebuffer):
  readbuffer.append(readByte())

# close serial connection
ser.close()

# compare with expected
if args.expected != None:
  if len(expectbuffer) != len(readbuffer):
    print(f"received {len(readbuffer)} bytes. expected {len(expectbuffer)} bytes")
    sys.exit(1)
  for i in range(len(expectbuffer)):
    if readbuffer[i] != expectbuffer[i]:
      print(f"unexpected byte number {i + 1}. received {readbuffer[i]}. expected {expectbuffer[i]}")
      sys.exit(1)

# write output hex file
if args.output != None:
  file = open(args.output, "w")
  file.write("".join(f"{int(value):02X}\n" for value in readbuffer))
