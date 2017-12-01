import serial
import vesna.alh

def main():
  f = serial.Serial('/dev/ttyS2', 115200, timeout=10)
  node = vesna.alh.ALHTerminal(f)

  print("Spectrum sensing configurations test.")
  print(node.get("sensing/deviceConfigList"))

main()
