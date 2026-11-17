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
- Integrate the scaling into [-1;1] for the software INT8 implementation

# Meeting 4 (03/11/2026)
- Fred and Robert not here, we do not know how far they are (Fred says he has something on a branch)
    - If HW test is not done (as of now), the task needs to be taken over by someone else who can finalize it
        - We need it
        - Robert needs to also help on other parts of the project before it/they becomes too complex to get up to speed with
- FFTseq has been set up, but not done at all (NEEDS TO BE DONE ASAP)
- UART IO has been made, needs testing
- Bandpass filter has been started on, but needs corrections (generally not "done")
- We have "synthesized" the combinatorical versions (with 32 samples), but failed (too many IOs)
    - Should work with UART (which should only have 2x IO)
- MADS BRINGS CAKE NEXT WEEK (maybe next week again)
- Besides our in-class meetings on Tuesdays, we would like to have an additional status meeting (for like, 30 minutes so that it can fit into a tight schedule + you don't have to sit at home)
    - Marilouise is suggesting either Saturday or Sunday
    - Sebastian prefers Saturday

### Robert's Update (Added in after the meeting)
- More hardware tests completed that should have desired coverage
- Next step is to synthesize and carry out physical tests
- Also available to assist in another part, will discuss with others online prior to next week

### Fred's Update
* IFFTComb implemented in Chisel (currently on its own branch)
* Two simple hardware tests for the IFFTComb. 

## What now?
- Marilouise continues on the filter
- Mads works on seq. FFT
- Sebastian tests UART and would like to make a script to test it automatically(?)
- Fred and Robert needs to figure out what they're doing

## Deadlines
- We are setting a deadline for ALL implementation on **12:00 (lunchtime) Tuesday the 17th of November**
- Presentation done **12:00 Tuesday the 1st of December**
- Readme and final repo done **17:00 Tuesday the 1st of December**
    - Martin is grading our projects in the evening after our presentation

# Meeting 5 (10/11/2026)
## What have we done?
- We have been bad at using GitHub projects (the kanban board)
- We had plans of having a meeting Saturday, but it was never done
    - We should have a meeting this weekend, we should also specify a time
    - We do it Sunday at 12:00 (for a 15-30 minute meeting) presumably on discord
- Marilouise has made the Bandpass filter module and a very simple test for it
- Frederik has made the sequential iFFT
- Mads has made the sequential FFT + combined FFT-filter-iFFT
- Robert has worked on some fuzzing tests (which is not yet working?)
- Sebastian has made UART tests

## What now?
- Seb: Write a python program to communicate with the UART test to see if it works
- If we assume the UART works, we can synthesize the sequential FFT
    - Replace the "EmptyDsp" with "FFTDsp"
- Robert: Fuzzing test on the board (can only be done when the UART tests works on the board)
- Mads: Fixing GitHub CI
- Mari and Fred will start looking into the microphone/speaker (and whether it is even doable with our deadlines)

## Notes
- The board does not have an on-board speaker, only an on-board microphone and an "audio out" female jack
- At some point Robert and Sebastian will have a small sit-down where they run all tests

# Meeting 6 (15/11/2026)

## What has been done
- Marylou and Fred started a PCM module and a MicTop (the "legoblock" before the DSP topmodule), we need to discuss some parameters and look more into the MicTop
- The audio out (speaker part) has not been looked into yet
- Python UART is being worked on today

- Marylou and Fred reevaluates the "MicTop"
- Mads can look into the "audioio top"
- Robert can look into the speaker
	- The GitHub with the PDM Verilog probably also has a audio-out module. Look into that. The link is in the PdmDes verilog file.
	- 8 bit input, 1 bit output
- Seb keeps working on the python UART

# Meeting 7 (17/11/2026)

## What has been done
- Mads has done things (wrote a message as he is sick)
- Seb finished the python program to interface with the UART top on the FPGA (works) + synthesized the DSP which has massive timing violations and massive area usage (needs to be fixed!)
	- We needs to split up the DSP module, so that there is a topmodule for only the FFT (so that it can be synthesized by itself), and all three FFT-filter-iFFT
- Marylou has started writing some tests for the microphone, but needs to make sure that they actually test for the things we want to test + that the module actually behaves how we want

## What needs to be done
- Python files for recording audio for the UART version + play the resulting audio (Seb)
- The thing on the FPGA board still doesn't work. Needs to be fixed. PRIORITY #1.
	- Mads (and maybe Seb) should have a look at the current implementation
- Still look into the speaker (if draft is not done on Friday, inform group so we can act accordingly)
- Hex files for testvectors for tests on board (workflow; have a Scala (or python) file generate them)
- Start writing the README and create figures (needs to be done-done the 29th at midnight)
- Finish the simple microphone test (Marylou; should be done today hopefully)