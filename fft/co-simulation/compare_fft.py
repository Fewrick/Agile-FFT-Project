"""Compare fresh signed hardware/software exports with an independent NumPy FFT."""
import argparse
import json
from pathlib import Path

import numpy as np
import matplotlib.pyplot as plt
from fft import fft

DIRECTORY = Path(__file__).resolve().parent
FFT_BITS = 13
DFT_BITS = 13
SCALA_BITS = 32

def load_hex(path: Path, bits: int) -> np.ndarray:
    """Decode the actual bit width, including non-nibble-aligned signed HEX."""
    values = []
    for line in path.read_text().splitlines():
        if not line.strip():
            continue
        value = int(line.strip(), 16)
        if not 0 <= value < (1 << bits):
            raise ValueError(f'{path}: HEX value does not fit {bits} bits')
        if value & (1 << (bits - 1)):
            value -= 1 << bits
        values.append(value)
    return np.asarray(values, dtype=np.int64)

def load_fft_hex(real_path: Path, imag_path: Path, expected_length: int, bits: int = 32) -> np.ndarray:
    real, imag = load_hex(real_path, bits), load_hex(imag_path, bits)
    if len(real) != expected_length or len(imag) != expected_length:
        raise ValueError('FFT output length mismatch')
    return real.astype(float) + 1j * imag.astype(float)

def load_fft_float(real_path: Path, imag_path: Path) -> np.ndarray:
    real, imag = np.atleast_1d(np.loadtxt(real_path)), np.atleast_1d(np.loadtxt(imag_path))
    if real.shape != imag.shape:
        raise ValueError('Floating-point component length mismatch')
    return real + 1j * imag

def metrics(reference, actual):
    error = np.abs(reference - actual)

    return {
        "max_error": float(np.max(error)),
        "mean_error": float(np.mean(error))
    }

def compare_case(directory: Path, samples: np.ndarray) -> tuple[dict, dict]:
    n = len(samples)
    re, im = fft(samples)
    outputs = {
        'NumPy': np.array(re) + 1j * np.array(im),
        'Scala FP': load_fft_float(directory / 'scala_fp_real.txt', directory / 'scala_fp_imag.txt')
    }
    for name, prefix, bits in [('Scala Radix-2', 'scala_radix2', SCALA_BITS),
                               ('Chisel FFT', 'chisel_fft', FFT_BITS),
                               ('Chisel DFT', 'chisel_dft', DFT_BITS)]:
        outputs[name] = load_fft_hex(directory / f'{prefix}_real.hex', directory / f'{prefix}_imag.hex', n, bits)
    report = {
        f'NumPy vs {name}': metrics(outputs['NumPy'], output)
        for name, output in outputs.items()
        if name != 'NumPy'
    }
    report['Scala Radix-2 vs Chisel FFT'] = metrics(
        outputs['Scala Radix-2'],
        outputs['Chisel FFT']
    )
    return outputs, report

def plot(samples: np.ndarray, outputs: dict, path: Path) -> None:
    fig, axes = plt.subplots(5, 1, figsize=(12, 16), constrained_layout=True)
    axes[0].plot(np.arange(len(samples)), samples, '.-')
    axes[0].set(title='Shared signed 8-bit input', xlabel='Sample index', ylabel='Amplitude')
    # rfftfreq labels the even-length Nyquist bin as +0.5, not -0.5.
    frequency = np.fft.rfftfreq(len(samples))
    count = len(frequency)
    for name, style in [('NumPy', 'o-'), ('Scala FP', 'x--')]:
        axes[1].plot(frequency, np.abs(outputs[name][:count]), style, label=name)
    axes[1].set(title='NumPy vs Scala floating-point FFT', ylabel='Magnitude |X[k]|',
                xlabel='Nonnegative frequency (cycles/sample)')
    for name, style in [('NumPy', 'o-'), ('Scala Radix-2', 's--'),
                        ('Chisel FFT', 'x:'), ('Chisel DFT', '+-.')]:
        axes[2].plot(frequency, np.abs(outputs[name][:count]), style, label=name)
    axes[2].set(title='Unnormalized full-range magnitude', ylabel='Magnitude |X[k]|',
                xlabel='Nonnegative frequency (cycles/sample)')
    for name, spectrum in outputs.items():
        if name == 'NumPy':
            continue
        error = spectrum - outputs['NumPy']
        axes[3].plot(np.arange(len(samples)), error.real, '.-', label=name)
        axes[4].plot(np.arange(len(samples)), error.imag, '.-', label=name)
    axes[3].set(title='Real-component errors relative to NumPy (all bins)', ylabel='Real error', xlabel='FFT bin k')
    axes[4].set(title='Imaginary-component errors relative to NumPy (all bins)', ylabel='Imaginary error', xlabel='FFT bin k')
    for ax in axes:
        ax.grid(True, alpha=.3)
    for ax in axes[1:]:
        ax.legend()
    path.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(path, dpi=160)
    print(f'Saved figure to {path}')

def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--samples', type=int, default=32)
    parser.add_argument('--input-hex', type=Path, default=DIRECTORY / 'sine.hex')
    parser.add_argument('--save', type=Path, help='Save plot without opening it')
    parser.add_argument('--report', type=Path, default=DIRECTORY / 'comparison_metrics.json')
    args = parser.parse_args()
    path = args.input_hex
    if not path.is_absolute() and path.parent == Path('.'):
        path = DIRECTORY / path
    samples = load_hex(path, 8)
    if len(samples) != args.samples:
        raise ValueError("Input sample count differs from CLI")
    outputs, report = compare_case(DIRECTORY, samples)
    for name, result in report.items():
        print(f"{name}: "
              f"max error = {result['max_error']:.6f}, "
              f"mean error = {result['mean_error']:.6f}")
    peak = int(np.argmax(np.abs(outputs['NumPy'][:len(samples)//2+1])))
    print(f'Nonnegative NumPy peak: bin {peak}, magnitude {abs(outputs["NumPy"][peak]):.9g}')
    result = dict(shared=report, peak_bin=peak, peak_magnitude=float(abs(outputs['NumPy'][peak])))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2) + '\n')
    plot(samples, outputs, args.save or DIRECTORY / 'fft_comparison.png')
    print("PASS: FFT comparisons completed successfully")
    if args.save is None:
        plt.show()

if __name__ == '__main__':
    main()