import argparse
from pathlib import Path

import numpy as np
from fft import sine_input

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--samples", type=int, default=32)
    parser.add_argument("--cycles", type=float, default=2)
    parser.add_argument("--amplitude", type=float, default=127)
    parser.add_argument("--output", type=Path, default=Path(__file__).parent / "sine.hex")
    args = parser.parse_args()

    samples = sine_input(args.samples, args.cycles, args.amplitude, integer_input=True)

    if np.any(samples < -128) or np.any(samples > 127):
        raise ValueError("Sine samples exceed signed 8-bit range")

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        "".join(f"{int(value) & 0xFF:02X}\n" for value in samples)
    )

    print(f"Generated {args.output}")

if __name__ == "__main__":
    main()