.PHONY: image sine fft-output graph clean rtl synth-report fpga-reports

SAMPLES ?= 32
CYCLES ?= 2
AMPLITUDE ?= 127
TOP ?= UartTop
RTL_TOP ?= top.$(TOP)

image: fft-output graph

sine:
	python co-simulation/generate_sine.py \
		--samples $(SAMPLES) \
		--cycles $(CYCLES) \
		--amplitude $(AMPLITUDE)

fft-output: sine
	sbt "testOnly cosimulation.FFTOutputTest"

graph:
	MPLCONFIGDIR=/tmp/fft-matplotlib MPLBACKEND=Agg \
	python co-simulation/compare_fft.py \
		--save co-simulation/fft_comparison.png

UartTop.v:
	sbt "runMain top.UartTop"

# Always regenerate RTL so reports do not use an old generated file.
rtl:
	sbt "runMain $(RTL_TOP)"

synth-report: rtl
	$(MAKE) -C vivado synth-report

fpga-reports: rtl
	$(MAKE) -C vivado build
	$(MAKE) -C vivado reports

.PHONY: fpga
fpga: UartTop.v
	cd vivado && make

clean:
	rm -rf test_run_dir
	rm -f co-simulation/*.hex
	rm -f co-simulation/*.txt
	rm -f co-simulation/fft_comparison.png
	rm -f co-simulation/comparison_metrics.json
	rm -rf co-simulation/__pycache__/
	rm UartTop.v
	rm UartTop.fir
	cd vivado && make clean
