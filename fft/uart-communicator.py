## Some code snippets taken from https://github.com/sebasabes2/processinator-bootloader/blob/main/bootloader.py

import sys
import time
import serial
import serial.tools.list_ports

import argparse

parser = argparse.ArgumentParser()
parser.add_argument("-i", "--input", required=True, help="input file")
group = parser.add_mutually_exclusive_group()
group.add_argument("-o", "--output", help="output file")
group.add_argument("-x", "--expected", help="expected output")
parser.add_argument("-p" "--serial-port", help="serial port")
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
  ports = list(filter(lambda x: "USB" in x.description, serial.tools.list_ports.comports()))
  if (len(ports) == 0):
    print("unable to find derial port")
    sys.exit(1)
  if (len(ports) != 1):
    print("found multiple serial ports:\n" + '\n'.join(map(lambda x: x.device, ports)))
    sys.exit(1)
  print(f"found serial port: {ports[0].device}")
  return ports[0].device

port = args.p__serial_port
if port == None:
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

# if no output is given
if args.expected == None and args.output == None:
  print(f"received buffer: {", ".join(f"{int(value):02X}" for value in readbuffer)}")
