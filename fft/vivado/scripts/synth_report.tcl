namespace eval synth_report {}

proc synth_report::run {} {
    set top        [cfg::require TOP]
    set part       [cfg::require PART]
    set build_dir  [file normalize [cfg::require BUILD_DIR]]
    set report_dir [file join $build_dir reports]

    puts "============================================================"
    puts " Synthesis report"
    puts "============================================================"
    puts "Top:       $top"
    puts "Part:      $part"
    puts "Report dir:$report_dir"

    # Use the same sources, selected top, and XDC as the bitstream flow.
    project::create

    synth_design -top $top -part $part

    # --------------------------------------------------------
    # Reports
    # --------------------------------------------------------

    file mkdir $report_dir

    report_utilization \
        -file [file join $report_dir synthesis_utilization.rpt]

    puts "============================================================"
    puts " Synthesis complete"
    puts " Report:"
    puts " [file join $report_dir synthesis_utilization.rpt]"
    puts "============================================================"
}
