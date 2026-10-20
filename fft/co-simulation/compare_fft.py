import argparse
from pathlib import Path

import numpy as np
import matplotlib.pyplot as plt

from fft import fft, sine_input

def load_hex(path: Path, bits: int) -> np.ndarray:
    """Read two's-complement HEX values as signed integers."""
    values = []
    with path.open() as file:
        for line in file:
            line = line.strip()
            if not line:
                continue
            value = int(line, 16)
            if value >= (1 << (bits - 1)):
                value -= 1 << bits
            values.append(value)
    return np.array(values, dtype=np.int64)

def load_fft_hex(real_path: Path, imag_path: Path, expected_length: int, bits: int = 32) -> np.ndarray:
    """Load separate real and imaginary FFT output files."""
    real = load_hex(real_path, bits)
    imag = load_hex(imag_path, bits)
    if len(real) != expected_length or len(imag) != expected_length:
        raise ValueError("FFT output length mismatch")
    return real.astype(np.float64) + 1j * imag.astype(np.float64)

def load_fft_float(real_path: Path, imag_path: Path) -> np.ndarray:
    real = np.atleast_1d(np.loadtxt(real_path))
    imag = np.atleast_1d(np.loadtxt(imag_path))
    return real + 1j * imag

def save_input(path: Path, samples: np.ndarray) -> None:
    """Save signed 8-bit samples as two's-complement HEX."""
    with path.open("w") as file:
        for sample in samples:
            value = int(sample)
            if not -127 <= value <= 127:
                raise ValueError(f"Input sample outside [-127,127]: {value}")
            file.write(f"{value & 0xFF:02X}\n")

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--samples', type=int, default=64)
    parser.add_argument('--cycles', type=float, default=5.0, help='Sine cycles in the input block')
    parser.add_argument('--amplitude', type=float, default=127.0)
    parser.add_argument('--integer-input', action='store_true', help='Round sine to integer amplitudes for Scala/Chisel')
    parser.add_argument('--scala-real', type=Path, help='Scala real FFT HEX')
    parser.add_argument('--scala-imag', type=Path, help='Scala imaginary FFT HEX')
    parser.add_argument('--chisel-real', type=Path, help='Chisel real FFT HEX')
    parser.add_argument('--chisel-imag', type=Path, help='Chisel imaginary FFT HEX')
    parser.add_argument('--input-hex', type=Path, default=Path(__file__).resolve().parent / 'sine.hex', help='Write sine samples as 8-bit HEX')
    parser.add_argument('--save', type=Path, help='Save figure instead of displaying it')
    args = parser.parse_args()

    directory = Path(__file__).resolve().parent

    # Read exactly the same input used by Scala/Chisel
    samples = load_hex(directory / "sine.hex", 8)

    # Python floating-point reference
    real, imag = fft(samples)

    outputs = {
        "Python (NumPy)": np.array(real) + 1j * np.array(imag),
        "Scala FP": load_fft_float(directory / "scala_fp_real.txt", directory / "scala_fp_imag.txt"),
        "Scala Radix-2": load_fft_hex(directory / "scala_radix2_real.hex", directory / "scala_radix2_imag.hex", len(samples), 32),
        "Chisel FFT": load_fft_hex(directory / "chisel_fft_real.hex", directory / "chisel_fft_imag.hex", len(samples), 8),
        "Chisel DFT": load_fft_hex(directory / "chisel_dft_real.hex", directory / "chisel_dft_imag.hex", len(samples), 8)
    }

    # Scala FFT
    if args.scala_real or args.scala_imag:
        if not (args.scala_real and args.scala_imag):
            parser.error("Both --scala-real and --scala-imag are required")
        outputs['Scala'] = load_fft_hex(args.scala_real, args.scala_imag, len(samples))

    # Chisel FFT
    if args.chisel_real or args.chisel_imag:
        if not (args.chisel_real and args.chisel_imag):
            parser.error("Both --chisel-real and --chisel-imag are required")
        outputs['Chisel'] = load_fft_hex(args.chisel_real, args.chisel_imag, len(samples))

    fig, (ax_input, ax_fft) = plt.subplots(2, 1, figsize=(11, 7), constrained_layout=True)
    ax_input.plot(np.arange(len(samples)), samples, marker='.', markersize=4)
    ax_input.set_title('Sine-wave input (shared by all implementations)')
    ax_input.set_xlabel('Sample index')
    ax_input.set_ylabel('Amplitude')
    ax_input.grid(True, alpha=0.3)

    bins = np.fft.fftfreq(len(samples), d=1.0)
    for name, spectrum in outputs.items():
        ax_fft.plot(bins[:len(samples)//2 + 1], np.abs(spectrum[:len(samples)//2 + 1]), marker='.', label=name)
    ax_fft.set_title('FFT magnitude comparison (positive frequencies)')
    ax_fft.set_xlabel('Frequency (cycles/sample)')
    ax_fft.set_ylabel('Magnitude |X[k]|')
    ax_fft.grid(True, alpha=0.3)
    ax_fft.legend()

    reference = outputs['Python (NumPy)']
    for name, spectrum in list(outputs.items())[1:]:
        print(f'{name}: max complex-bin error = {np.max(np.abs(spectrum - reference)):.6g}')

    if args.save:
        fig.savefig(args.save, dpi=160)
        print(f'Saved figure to {args.save}')
    else:
        plt.show()

if __name__ == '__main__':
    main()
