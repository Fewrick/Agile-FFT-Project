.PHONY: image sine fft-output graph clean

SAMPLES ?= 32
CYCLES ?= 2
AMPLITUDE ?= 127

image: fft-output graph

sine:
	python co-simulation/generate_sine.py \
		--samples $(SAMPLES) \
		--cycles $(CYCLES) \
		--amplitude $(AMPLITUDE)

fft-output: sine
	sbt "testOnly comparison.FFTOutputTest"

graph:
	MPLCONFIGDIR=/tmp/fft-matplotlib MPLBACKEND=Agg \
	python co-simulation/compare_fft.py \
		--save co-simulation/fft_comparison.png

clean:
	rm -rf test_run_dir
	rm -f co-simulation/*.hex
	rm -f co-simulation/*.txt
	rm -f co-simulation/fft_comparison.png
	rm -f co-simulation/comparison_metrics.json
	rm -rf co-simulation/__pycache__/