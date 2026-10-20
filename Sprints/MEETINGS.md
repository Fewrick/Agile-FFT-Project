# Meeting notes

## Meeting 1 (29/09/2026)
- First sprint is to do FFT and not connect it to anything
- Choice of algorithm to use for the FFT - Radix 2 (most simple of 4 and 2^2 counterparts)
- Compare software and hardware execution
- Integer based FFT
- Static precision
- Translate PDM microphone 1-bit output to 8-bit input Registers 
- The signal processing should happen for 8-bit inputs at 12KHz
- 


### Tasks:

#### Group
- Create initial schematic (Agree on blackbox/interface in advance)

#### Individual
- Create Hardware Test
- Create Hardware Implementation
- Create Software Implementation INT8 (Radix 2) and FP32 implementation

## Meeting 2 (20/10/2026)
- Hardware Impl. group has worked on a DFT and looked into how to estimate a squareroot in chisel using an algorithm called "Alpha max plus beta min". However, this will probably need to be discarded, as the FFT algorithm works completely different.
    - We still need to look into making the actual FFT implementation.
    - We still need to look into how to estimate a squareroot.

- Software impl. group has made INT8 (Radix 2) and FP32 implementation + a very simple test show case of the scala implementation
    - We are going to have to look into how to handle imagionary calculations. 

- Hardware test group has not created much
    - For next week, we will need to have the same tests as on the software side

- We need to agree on how to quantize the signal values
    - The input are 8 bit signed, where 127 encodes 1 (128 would be seen as overflow) and -127 encodes -1
    - Calculations shouldn't trunkate (throw away smallest bits) before the very end
    - Later we can maybe look into whether to also trunkate mid-way through the calculations actually damages performance or accuracy

- We don't need to look into the squareroot implementation currently (might need to do later), because we can just have the result be a tuple of the real and imaginary value and make sure that these are the same in the hardware and software iimplementations.

- In the very end, when we want to do the signal processing, we need to have done the squareroot.
    - This might be in a later sprint.

### Tasks:

#### Group
- Presentation (next week)
    - Take a sample, take an independent FFT (function from a library), put a picture of that spectrum on one slide
    - Compare our software FP implementation on the same sample, show graph/spectrum
    - Run our software INT8 implementation on the same sample, shpw spectrum
    - Chisel test that compares the INT8 sofware impl. with the hardware impl., show quantitatively that they are the exact same

#### Individual
- Find independent FFT algorithm and make graphs
- Create Hardware Test
- Create Hardware Implementation of the FFT instead of DFT (don't consider squareroot)
    - Change the output of FFT to a Real and Imaginary part (it's already like that in software)
    - Run tests that compares to software implementation
- Make graphs/spectrum of both software implementation
