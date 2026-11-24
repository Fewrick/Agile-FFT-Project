# ============================================================
# Configuration
# ============================================================

PYTHON ?= python
SBT ?= sbt
SBT_ARGS ?= -Dsamples=$(SAMPLES) -Dwidth=$(WIDTH) -DsampleRate=$(SAMPLE_RATE) -DclkFreq=$(CLK_FREQ) $(if $(RECORDING_SAMPLES),-DrecordingSamples=$(RECORDING_SAMPLES))

WAVEFORM ?= sine
PHASE ?= 0
SEED ?= 0
SAMPLES ?= 32
WIDTH ?= 8
SAMPLE_RATE ?= 12500
CLK_FREQ ?= 100000000
# Empty delegates frame-rounded one-second default to Scala elaboration.
RECORDING_SAMPLES ?=
CYCLES ?= 2
AMPLITUDE ?= 127
FREQUENCY ?= $(CYCLES)
TOP ?= UartTop

# ============================================================
# Waveform generation
# ============================================================

.PHONY: image sine
image: fft-output
	$(MAKE) graph

# Keep the sine target for existing callers.
sine:
	$(MAKE) generate-hex WAVEFORM=sine

.PHONY: generate-hex generate-hex-all
generate-hex:
	$(PYTHON) co-simulation/generate_hex.py --waveform $(WAVEFORM) \
		--samples $(SAMPLES) --frequency $(FREQUENCY) --amplitude $(AMPLITUDE) \
		--phase $(PHASE) --seed $(SEED) --replace

generate-hex-all:
	$(PYTHON) co-simulation/generate_hex.py --suite --samples $(SAMPLES) --replace

# ============================================================
# Co-simulation and plots
# ============================================================

.PHONY: test-generated-hex
test-generated-hex: generate-hex-all
	$(SBT) "testOnly cosimulation.GeneratedHexTest"

.PHONY: fft-output graph
fft-output:
	@test "$(SAMPLES)" = 32 || (echo "FFTOutputTest requires SAMPLES=32"; exit 1)
	$(MAKE) generate-hex
	FFT_INPUT="$(CURDIR)/co-simulation/generated_hex/$(SAMPLES)/$(WAVEFORM).hex" FFT_PYTHON="$(PYTHON)" \
		$(SBT) "testOnly cosimulation.FFTOutputTest"

graph:
	MPLCONFIGDIR=/tmp/fft-matplotlib MPLBACKEND=Agg \
	$(PYTHON) co-simulation/compare_fft.py --samples $(SAMPLES) \
		--input-hex "$(CURDIR)/co-simulation/generated_hex/$(SAMPLES)/$(WAVEFORM).hex" \
		--save co-simulation/fft_comparison.png

# ============================================================
# RTL generation
# ============================================================

.PHONY: rtl FORCE

FORCE:

# Scala emits Verilog, FIRRTL, annotations, and black-box resources into generated/.
generated/$(TOP).v: $(wildcard src/main/scala/*/*.scala) FORCE
	$(SBT) $(SBT_ARGS) "runMain top.$(TOP)"

rtl: generated/$(TOP).v

# ============================================================
# FPGA builds and reports
# ============================================================

.PHONY: synth-report fpga-reports fpga fpga-flash

synth-report: generated/$(TOP).v
	$(MAKE) -C vivado synth-report TOP="$(TOP)"

fpga-reports: generated/$(TOP).v
	$(MAKE) -C vivado build TOP="$(TOP)"
	$(MAKE) -C vivado reports

fpga: generated/$(TOP).v
	$(MAKE) -C vivado build TOP="$(TOP)"

fpga-flash:
	$(MAKE) -C vivado flash-linux

# ============================================================
# Cleanup
# ============================================================

.PHONY: clean
clean:
	rm -rf test_run_dir
	rm -f co-simulation/fft_comparison.png
	rm -f co-simulation/comparison_metrics.json
	rm -rf co-simulation/__pycache__/
	rm -rf co-simulation/generated_hex
	rm -rf co-simulation/output_hex
	rm -rf generated
	$(MAKE) -C vivado clean