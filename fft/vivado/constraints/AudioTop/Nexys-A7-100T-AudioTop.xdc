# Nexys A7-100T pin assignments, from Digilent's master XDC:
# https://github.com/Digilent/digilent-xdc/blob/master/Nexys-A7-100T-Master.xdc
# 100 MHz oscillator and active-high center-button reset.
set_property -dict { PACKAGE_PIN E3 IOSTANDARD LVCMOS33 } [get_ports {clock}]
create_clock -name sys_clk_pin -period 10.000 -waveform {0 5} [get_ports {clock}]
set_property -dict { PACKAGE_PIN N17 IOSTANDARD LVCMOS33 } [get_ports {reset}]

# Onboard PDM microphone and PWM audio amplifier.
set_property -dict { PACKAGE_PIN H5 IOSTANDARD LVCMOS33 } [get_ports {io_streamIn}]
set_property -dict { PACKAGE_PIN J5 IOSTANDARD LVCMOS33 } [get_ports {io_micClkOut}]
set_property -dict { PACKAGE_PIN A11 IOSTANDARD LVCMOS33 } [get_ports {io_audPwm}]
set_property -dict { PACKAGE_PIN D12 IOSTANDARD LVCMOS33 } [get_ports {io_audSd}]

# BTNU captures, BTNL processes, BTNR plays the current recording.
# SW[15:0] is latched when processing starts.
set_property -dict { PACKAGE_PIN M18 IOSTANDARD LVCMOS33 } [get_ports {io_start}]
set_property -dict { PACKAGE_PIN P17 IOSTANDARD LVCMOS33 } [get_ports {io_process}]
set_property -dict { PACKAGE_PIN M17 IOSTANDARD LVCMOS33 } [get_ports {io_output}]
set_property -dict { PACKAGE_PIN J15 IOSTANDARD LVCMOS33 } [get_ports {io_mask[0]}]
set_property -dict { PACKAGE_PIN L16 IOSTANDARD LVCMOS33 } [get_ports {io_mask[1]}]
set_property -dict { PACKAGE_PIN M13 IOSTANDARD LVCMOS33 } [get_ports {io_mask[2]}]
set_property -dict { PACKAGE_PIN R15 IOSTANDARD LVCMOS33 } [get_ports {io_mask[3]}]
set_property -dict { PACKAGE_PIN R17 IOSTANDARD LVCMOS33 } [get_ports {io_mask[4]}]
set_property -dict { PACKAGE_PIN T18 IOSTANDARD LVCMOS33 } [get_ports {io_mask[5]}]
set_property -dict { PACKAGE_PIN U18 IOSTANDARD LVCMOS33 } [get_ports {io_mask[6]}]
set_property -dict { PACKAGE_PIN R13 IOSTANDARD LVCMOS33 } [get_ports {io_mask[7]}]
set_property -dict { PACKAGE_PIN T8 IOSTANDARD LVCMOS18 } [get_ports {io_mask[8]}]
set_property -dict { PACKAGE_PIN U8 IOSTANDARD LVCMOS18 } [get_ports {io_mask[9]}]
set_property -dict { PACKAGE_PIN R16 IOSTANDARD LVCMOS33 } [get_ports {io_mask[10]}]
set_property -dict { PACKAGE_PIN T13 IOSTANDARD LVCMOS33 } [get_ports {io_mask[11]}]
set_property -dict { PACKAGE_PIN H6 IOSTANDARD LVCMOS33 } [get_ports {io_mask[12]}]
set_property -dict { PACKAGE_PIN U12 IOSTANDARD LVCMOS33 } [get_ports {io_mask[13]}]
set_property -dict { PACKAGE_PIN U11 IOSTANDARD LVCMOS33 } [get_ports {io_mask[14]}]
set_property -dict { PACKAGE_PIN V10 IOSTANDARD LVCMOS33 } [get_ports {io_mask[15]}]

# LED0: recording stored; LED1: DSP processing finished; LED2: playback active.
# LED[7:3] are off.
set_property -dict { PACKAGE_PIN H17 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[0]}]
set_property -dict { PACKAGE_PIN K15 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[1]}]
set_property -dict { PACKAGE_PIN J13 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[2]}]
set_property -dict { PACKAGE_PIN N14 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[3]}]
set_property -dict { PACKAGE_PIN R18 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[4]}]
set_property -dict { PACKAGE_PIN V17 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[5]}]
set_property -dict { PACKAGE_PIN U17 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[6]}]
set_property -dict { PACKAGE_PIN U16 IOSTANDARD LVCMOS33 } [get_ports {io_streamOut[7]}]
