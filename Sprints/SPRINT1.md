## Plan
Setup project 

Initial FFT should handle 32 inputs of 8 bits

## Design
- 8-bit FFT
- 8-bit inverse FFT

Input: 32 INT8 values at that should arrive together
Output: 32 INT8 values that are output together

## Implement
Initially there are three development paths
- Implement FFT in hardware (Chisel) with focus on Radix-2 algorithm using INT8 precision
- Implement FFT in software (Scala) with foxus on Radix-2 algorithm using INT8 precision and full scale fft using floating points for reference

## Test
- Create tests that verify firstly the hardware implementation with precomputed values and one that compares the software and hardware implementation (possibly with randomly generated inputs)

## Review/Demo

## Retrospective/Backlog