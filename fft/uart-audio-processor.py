import argparse
import audiofile
import serial
import serial.tools.list_ports
import time
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument("-i", "--input", required=True, help="input file")
parser.add_argument("-o", "--output", required=True, help="output file")
parser.add_argument("-p" "--serial-port", help="serial port")
args = parser.parse_args()

signal, sampling_rate = audiofile.read(args.input)

# make signal mono:
signal = list(map(lambda x: (x[0] + x[1]) * 0.5, list(zip(signal[0], signal[1]))))

# convert to 8 bit signed integers
signal = list(map(lambda x: int(x * 127) % 256, signal))

# convert to byte array
signal = bytearray(signal)

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

# process signal
def writeWindow(ser, window):
  ser.write(window)

def readByte(ser):
  waits = 0
  while ser.inWaiting() == 0:
    if waits == 100:
      print("Uart timed out")
      sys.exit(1)
    time.sleep(0.01)
    waits = waits + 1
  return ser.read()[0]

def readWindow(ser):
  readbuffer = bytearray()
  while len(readbuffer) < 1024:
    readbuffer.append(readByte(ser))
  return readbuffer

outputbuffer = bytearray()

i = 0
while i + 1024 <= len(signal):
  print(f"processing samples {i}:{i + 1023}/{len(signal)}")
  window = signal[i:i+1024]
  writeWindow(ser, window)
  outputbuffer.extend(readWindow(ser))
  i += 1024

signal = outputbuffer

# convert signal to float
signal = np.array(list(map(lambda x: float(((x + 128) % 256) - 128) / 127, signal)))

# write audiofile
audiofile.write(args.output, signal, sampling_rate)
