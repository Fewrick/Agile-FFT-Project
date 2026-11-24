# FFT + signal processing + inverse-FFT  
Repository for the DTU Agile Hardware Design course [Agile Hardware Design [02203]](https://lifelonglearning.dtu.dk/compute/enkeltfag/agil-hardwareudvikling/), containing the project files for group 10. 

## Introduction / Motivation 
We wanted to explore how digital signal processing could be implemented directly in hardware. We were interested in how an FFT algorithm works, because it gives an interesting way to break sound into frequency components.  We wanted to work with the whole process of using an FFT to  break down an audio clip, filtering specific selected frequencies, and applying an inverse FFT to reconstruct the clip. Thus learning how signal and music processing works when implemented in hardware. 

The idea is to generate any FFT (Fast Fourier Transform) in hardware from configurable design parameters such as the input size and the amount of stages. This would take some short sound clip and turn it into the frequency domain. In order to see some change to our signal, we plan to add some simple signal processing in the form of a filter (e.g. filtering low frequencies). Afterwards, we apply inverse-FFT to get the signal back into the time domain, such that we can output the processed signal on a small speaker.

### Creators:
- Marilouise Arbøl - (s214401)
- Sebastian Holdt - (s235475)
- Mads Pedersen - (s235472)
- Frederik Lauritz Udby - (s224781)
- Robert Yoo - (s253067)
## Features
The projects central feature is a parameterized, hardware-based audio pipeline that demonstrates how FFT analysis, frequency filtering, and inverse transformation can be combined to process sound. The most important features are listed below. (Not ordered by importance)
- **Configurable FFT size and sample width:** Transform length and input width are design parameters. The transform supports sizes that are powers of two; the integrated audio design defaults to 32 samples and 8-bit input.
- **Forward FFT:** Converts a frame of real audio samples into complex frequency-domain data using a radix-2 FFT.
- **Inverse FFT:** Converts the filtered frequency-domain data back into time-domain samples. 
- **Frequency-domain filtering:** A 16-bit mask controls which groups of frequency bins pass through and which are zeroed. The bin mapping accounts for the FFT's mirrored spectrum.
- **Complete audio-processing chain:** The integrated design captures a block of audio, runs the FFT, applies the filter, performs the inverse FFT, and makes the processed samples available for playback. 
- **Microphone input:** Accepts a PDM microphone signal, decimates it to PCM samples, and captures samples for processing.
- **Speaker output:** Outputs processed audio through a PWM speaker interface.
- **Hardware generation and testing:** The chisel design can generate Verilog, and the repository includes tests for transform behavior, filtering, audio I/O, and top-level integration. 

## Project Structure

```text
Agile-FFT-Project/
├── fft/                          # Main FFT and audio-processing project
│   ├── src/
│   │   ├── main/
│   │   │   ├── resources/        # Verilog microphone deserializer
│   │   │   └── scala/
│   │   │       ├── arithmetic/   # Arithmetic helpers
│   │   │       ├── audioIO/      # Microphone, PCM and speaker interfaces
│   │   │       ├── cosimulation/ # Software FFT reference models
│   │   │       ├── dsp/          # DSP pipelines and processing control
│   │   │       ├── fft/          # FFT, inverse FFT and DFT modules
│   │   │       ├── filter/       # Frequency-domain filter
│   │   │       ├── interfaces/   # Complex samples and streaming interfaces
│   │   │       ├── timing/       # Sample-rate timing
│   │   │       └── top/          # Audio and UART top-level modules
│   │   └── test/scala/           # Component, integration and reference tests
│   │       ├── arithmetic/
│   │       ├── audioIO/
│   │       ├── cosimulation/
│   │       ├── fft/
│   │       ├── filter/
│   │       ├── top/
│   │       └── util/             # Shared test helpers
│   ├── co-simulation/            # Python reference and comparison scripts
│   ├── vivado/
│   │   ├── constraints/          # FPGA pin and timing constraints
│   │   ├── scripts/              # Vivado build and programming scripts
│   │   ├── config.mk
│   │   └── Makefile
│   ├── build.sbt                 # Scala and Chisel build configuration
│   ├── Makefile
│   ├── requirements.txt          # Python dependencies
│   ├── uart-audio-processor.py   # UART audio processing utility
│   └── uart-communicator.py      # UART communication utility
├── Sprints/                      # Sprint and meeting notes
└── README.md
```

# Build and Run (Prone to change)

## Prerequisites

- **Java 17** and **sbt** to compile the Chisel design and generate Verilog.
- **GNU Make** to use the project build targets.
- **Python 3** with the packages in `fft/requirements.txt` for waveform generation and co-simulation.
- **Verilator** and a C++ compiler for tests that use native simulation. CI uses Verilator 5.044 and Clang.
- **AMD Vivado** on the command line and a **Nexys A7-100T** board for FPGA builds and programming. Vivado is not required for Scala compilation or Verilog generation.

(Steps are generated with the help from my good friend and partner in crime, Copilot) 
(Therefore, we should probably take a proper look at them at some later time)

The commands below are run from the `fft/` directory:

```bash
cd fft
python3 -m pip install -r requirements.txt
```

### Compile and generate Verilog

Compile the Scala and Chisel sources:

```bash
sbt compile
```

Generate the microphone-to-speaker audio design:

```bash
make rtl TOP=AudioTop
```

Generated files are written to `fft/generated/`. The defaults are 32 samples and an 8-bit sample width. Override them when generating the design, for example:

```bash
make rtl TOP=AudioTop SAMPLES=64 WIDTH=8
```

`SAMPLES` must be a power of two and at least 2. The supplied AudioTop FPGA constraints are for an 8-bit output, so use `WIDTH=8` with those constraints.

*The default Makefile top is `UartTop`; select `TOP=AudioTop` explicitly for the FFT audio pipeline. `UartTop` currently uses `EmptyDsp`, not the FFT/filter/IFFT pipeline.* 
**(This will change when we are done with everything?)**

### Build and program the FPGA

With Vivado available on `PATH`, generate the audio design and build a bitstream for the Nexys A7-100T:

```bash
make fpga TOP=AudioTop
```

The bitstream is written to `fft/vivado/build/fft.bit`. Connect the board and program the already-built bitstream on Linux with:

```bash
make fpga-flash
```

This programming target uses Vivado's hardware server; ensure the board is detected and the server is available. The board constraints assign reset to the center button, capture start to the up button, and the 16 filter-mask bits to the switches. A switch set to 1 passes its mapped frequency bins; 0 suppresses them. Set the switches before starting capture.

### Run the tests and co-simulation

Generate the default sine input and run the test suite:

```bash
make sine PYTHON=python3
sbt test
```

To generate an FFT comparison plot using the default 32-sample input:

```bash
make image PYTHON=python3
```

The plot is saved as `fft/co-simulation/fft_comparison.png`. The comparison test currently requires `SAMPLES=32`.


# Design and implementation

## Audio capture and input buffer

## PDM-to-PCM Conversion
The onboard microphone represents sound using **Pulse Density Modulation (PDM)**. A stream of single bits in which the density of ones reflects the signal's amplitude. The FFT operates on numerical samples, so the microphone data must first be converted to **Pulse Code Modulation (PCM)**.

The `Microphone` module connects two stages. First, the Verilog `PdmDes` module generates the microphone clock, samples the incoming stream, and collects it into 16-bit words. each word is passed to the Chisel `PCM` module along with a *valid* signal. 

The `PCM` module counts the ones in ten consecutive words, giving one count for every 160 PDM bits. It then subtracts the midpoint of 80, so an equal number of ones and zeroes represents zero amplitude. Counts above or below that midpoint then represents positive and negative values. Finally, the result is scaled into a signed 8-bit sample, and a *valid* signal indicates when that sample is ready to be stored for processing. 

This reduction from 160 PDM bits to one PCM sample is called *decimation*. With the microphone clock running at approximately 2 MHz, it produces samples at approximately 12.5 kHz. The resulting block of PCM samples can then be passed to the FFT. 

**(For a more in-depth/detailed description of the different modules that make up this functionality, imagine that we have a separate readme file located near the files)**
 
## FFT 
*Explain how the radix-2 works as well. Maybe this should go in its own section*

## Frequency-Domain Filter

## Inverse FFT
The **Inverse Fast Fourier Transform (IFFT)** combines these frequency components to reconstruct a sequence of time-domain samples. This produces a modified version of the original sound, reflecting the frequencies retained or removed by the frequency-domain filter. 

The audio pipeline uses the `IFFTSeq` module, which receives one complex frequency valie at a time. Each value has a real and an imaginary component (Maybe leave out this, because it would be described earlier), both of which are used in the calculation. The module collects a complete block of values before processing it. This block size is configurable and must be a power of 2. 

`IFFTSeq` uses the same sequential radix-2 core (explain in FFT or its own section) as the forward FFT, configured to perform the inverse transform. The core stores incoming values in bit-reversed order and 

## Output Buffer and Speak Interface

## Top-Level Audio Controller

# Testing

