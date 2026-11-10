set script_dir [file dirname [file normalize [info script]]]
cd [file normalize [file join $script_dir ../..]]
set exit_code 0
set status_file ""

if {[catch {
    source [file join $script_dir common.tcl]
    cfg::parse {*}$argv
    set status_file [cfg::get NN2HL_STATUS_FILE ""]
    set bitstream [file normalize [cfg::require BITSTREAM]]
    if {![file isfile $bitstream]} { error "Bitstream does not exist: $bitstream" }
    open_hw_manager
    connect_hw_server -url [cfg::get HW_SERVER localhost:3121]
    set targets [get_hw_targets [cfg::get HW_TARGET *]]
    if {[llength $targets] != 1} { error "Expected one hardware target, found [llength $targets]. Set FPGA_HW_TARGET to select your cable." }
    current_hw_target [lindex $targets 0]
    open_hw_target
    set devices [get_hw_devices [cfg::get HW_DEVICE *]]
    if {[llength $devices] != 1} { error "Expected one FPGA, found [llength $devices]. Set FPGA_HW_DEVICE to select your FPGA." }
    set device [lindex $devices 0]
    current_hw_device $device
    refresh_hw_device -update_hw_probes false $device
    set_property PROGRAM.FILE $bitstream $device
    program_hw_devices $device
    puts "Programmed $device with $bitstream"
} message options]} {
    puts stderr "Programming failed: $message"
    set exit_code 1
}
if {$status_file ne ""} {
    if {[catch {
        set fd [open $status_file w]
        puts $fd $exit_code
        close $fd
    } message]} {
        puts stderr "Cannot record programming result: $message"
        set exit_code 1
    }
}
catch {close_hw_target}
catch {disconnect_hw_server}
catch {close_hw_manager}
exit $exit_code
