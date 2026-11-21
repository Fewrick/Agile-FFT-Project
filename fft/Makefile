.PHONY: image sine fft-output graph clean rtl synth-report fpga-reports

PYTHON ?= python
WAVEFORM ?= sine
PHASE ?= 0
SEED ?= 0
SAMPLES ?= 32
CYCLES ?= 2
AMPLITUDE ?= 127
FREQUENCY ?= $(CYCLES)
TOP ?= UartTop
RTL_TOP ?= top.$(TOP)

image: fft-output
	$(MAKE) graph

# Keep the sine target for existing callers.
sine:
	$(MAKE) generate-hex WAVEFORM=sine

.PHONY: generate-hex generate-hex-all generate-hex-suite
generate-hex:
	$(PYTHON) co-simulation/generate_hex.py --waveform $(WAVEFORM) \
		--samples $(SAMPLES) --frequency $(FREQUENCY) --amplitude $(AMPLITUDE) \
		--phase $(PHASE) --seed $(SEED) --replace

generate-hex-all:
	$(PYTHON) co-simulation/generate_hex.py --suite --samples $(SAMPLES) --replace

.PHONY: test-generated-hex
# Discover all existing generated_hex/<samples>/*.hex inputs without regenerating them.
test-generated-hex: generate-hex-all
	sbt "testOnly cosimulation.GeneratedHexTest"

fft-output:
	@test "$(SAMPLES)" = 32 || (echo "FFTOutputTest requires SAMPLES=32"; exit 1)
	$(MAKE) generate-hex
	FFT_INPUT="$(CURDIR)/co-simulation/generated_hex/$(SAMPLES)/$(WAVEFORM).hex" FFT_PYTHON="$(PYTHON)" \
		sbt "testOnly cosimulation.FFTOutputTest"

graph:
	MPLCONFIGDIR=/tmp/fft-matplotlib MPLBACKEND=Agg \
	$(PYTHON) co-simulation/compare_fft.py --samples $(SAMPLES) \
		--input-hex "$(CURDIR)/co-simulation/generated_hex/$(SAMPLES)/$(WAVEFORM).hex" \
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
	# Generated inputs are retained to preserve manually supplied regression vectors.
	rm -f co-simulation/fft_comparison.png
	rm -f co-simulation/comparison_metrics.json
	rm -rf co-simulation/__pycache__/
	rm -rf co-simulation/generated_hex
	rm -rf co-simulation/output_hex
	rm -f *.v
	rm -f *.fir
	cd vivado && make clean
