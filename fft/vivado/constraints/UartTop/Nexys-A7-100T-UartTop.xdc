# Nexys A7-100T pin assignments, from Digilent's master XDC:
# https://github.com/Digilent/digilent-xdc/blob/master/Nexys-A7-100T-Master.xdc
# 100 MHz oscillator and active-high center-button reset.
set_property -dict { PACKAGE_PIN E3 IOSTANDARD LVCMOS33 } [get_ports {clock}]
create_clock -name sys_clk_pin -period 10.000 -waveform {0 5} [get_ports {clock}]
set_property -dict { PACKAGE_PIN N17 IOSTANDARD LVCMOS33 } [get_ports {reset}]

# USB UART: board TXD drives FPGA RX; FPGA TX drives board RXD.
set_property -dict { PACKAGE_PIN C4 IOSTANDARD LVCMOS33 } [get_ports {io_rx}]
set_property -dict { PACKAGE_PIN D4 IOSTANDARD LVCMOS33 } [get_ports {io_tx}]
